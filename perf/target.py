import json
import os
import shlex
import socket
import subprocess
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PERF = ROOT / 'perf'
ACCESS_LOG = '/tmp/access-log/access.log'
K6_DIR = PERF / 'k6'
K6_ENV = {'K6_CSV_TIME_FORMAT': 'unix_milli'}


def read_env_file(path):
    env = {}
    if not path.exists():
        return env
    for line in path.read_text(encoding='utf-8').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            env[key.strip()] = value.strip()
    return env


def sh(cmd, **kwargs):
    return subprocess.run(cmd, check=True, capture_output=True, text=True, encoding='utf-8', **kwargs)


def k6_command(script, csv_path, env):
    cmd = ['k6', 'run', '--quiet', '--out', f'csv={csv_path}']
    for key, value in env.items():
        cmd += ['-e', f'{key}={value}']
    return cmd + [script]


def run_local_k6(script, run_dir, env):
    with open(run_dir / f'{Path(script).stem}.log', 'w', encoding='utf-8') as out:
        return subprocess.run(k6_command(script, run_dir / 'k6.csv', env), cwd=K6_DIR, stdout=out,
                              stderr=subprocess.STDOUT, env={**os.environ, **K6_ENV}).returncode


def to_mib(text):
    units = {'B': 1 / 2 ** 20, 'KiB': 1 / 1024, 'MiB': 1, 'GiB': 1024, 'kB': 1000 / 2 ** 20, 'MB': 1e6 / 2 ** 20, 'GB': 1e9 / 2 ** 20}
    for unit in sorted(units, key=len, reverse=True):
        if text.endswith(unit):
            return float(text[:-len(unit)]) * units[unit]
    return float('nan')


class LocalTarget:
    name = 'local'
    app_url = 'http://localhost:8081'
    cpu_limit = 2

    def __init__(self):
        self.env = read_env_file(ROOT / '.env')
        self.compose = ['docker', 'compose', '-f', str(ROOT / 'docker-compose.aws.yml'),
                        '-f', str(PERF / 'docker-compose.perf.yml')]
        self.app_container = 'ticketflow-aws-app-1'
        self.db_container = 'ticketflow-aws-mysql-1'
        self.database = self.env['MYSQL_DATABASE']

    def open(self):
        pass

    def close(self):
        sh(self.compose + ['down', '-v'], cwd=ROOT)

    def build(self):
        gradlew = 'gradlew.bat' if os.name == 'nt' else './gradlew'
        subprocess.run([str(ROOT / gradlew), 'bootJar', '-q'], check=True, cwd=ROOT, shell=os.name == 'nt')
        subprocess.run(self.compose + ['build', 'app'], check=True, cwd=ROOT)

    def mysql(self, sql):
        out = sh(['docker', 'exec', '-e', f'MYSQL_PWD={self.env["MYSQL_ROOT_PASSWORD"]}', self.db_container,
                  'mysql', '-uroot', '-N', '-B', self.env['MYSQL_DATABASE'], '-e', sql]).stdout
        return [row.split('\t') for row in out.splitlines() if row]

    def reset_database_and_app(self):
        sh(self.compose + ['down', '-v', '--remove-orphans'], cwd=ROOT)
        sh(self.compose + ['up', '-d', '--wait'], cwd=ROOT)

    def access_log(self):
        return sh(['docker', 'exec', self.app_container, 'cat', ACCESS_LOG]).stdout.splitlines()

    def run_k6(self, script, run_dir, env):
        return run_local_k6(script, run_dir, {**env, 'BASE_URL': self.app_url, 'JWT_SECRET': self.env['JWT_SECRET']})

    def sample_resources(self, metric):
        out = sh(['docker', 'stats', '--no-stream', '--format', '{{json .}}', self.app_container, self.db_container]).stdout
        row = {}
        for line in out.splitlines():
            stat = json.loads(line)
            prefix = 'app' if stat['Name'] == self.app_container else 'db'
            row[f'{prefix}_cpu_pct'] = float(stat['CPUPerc'].rstrip('%')) / self.cpu_limit
            row[f'{prefix}_mem_mib'] = to_mib(stat['MemUsage'].split('/')[0].strip())
        return row

    def cloud_metrics(self, started, ended):
        return {}


class AwsTarget:
    name = 'aws'

    def __init__(self):
        self.env = read_env_file(ROOT / '.env.aws')
        if not self.env:
            raise RuntimeError('.env.aws가 없다. perf/aws.env.example을 복사해 채운다')
        self.app_url = self.env['AWS_APP_URL'].rstrip('/')
        self.app_container = self.env.get('AWS_APP_CONTAINER', 'ticketflow-app')
        self.ec2_host = self.env.get('AWS_EC2_HOST', '')
        self.db_host = self.env['AWS_DB_HOST']
        self.db_port = int(self.env.get('AWS_DB_PORT', '3306'))
        self.tunnel_port = int(self.env.get('AWS_TUNNEL_PORT', '13306'))
        self.database = self.env['AWS_DB_NAME']
        self.k6_host = self.env.get('AWS_K6_HOST', '')
        self.k6_app_url = self.env.get('AWS_K6_APP_URL', self.app_url).rstrip('/')
        self.tunnel = None

    def ssh_options(self):
        return ['-i', self.env['AWS_SSH_KEY'], '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=accept-new']

    def login(self, host):
        return f'{self.env.get("AWS_EC2_USER", "ec2-user")}@{host}'

    def ssh_base(self, host=None):
        return ['ssh', *self.ssh_options(), self.login(host or self.ec2_host)]

    def remote(self, command):
        if not self.ec2_host:
            return sh(command, shell=True).stdout
        return sh(self.ssh_base() + [command]).stdout

    def open(self):
        if self.k6_host:
            sh(self.ssh_base(self.k6_host) + ['mkdir -p k6'])
            sh(['scp', '-q', *self.ssh_options(), *map(str, sorted(K6_DIR.glob('*.js'))), f'{self.login(self.k6_host)}:k6/'])
        if not self.ec2_host:
            self.connect_host, self.connect_port = self.db_host, self.db_port
            return
        self.tunnel = subprocess.Popen(self.ssh_base() + ['-N', '-L', f'{self.tunnel_port}:{self.db_host}:{self.db_port}'],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.connect_host, self.connect_port = '127.0.0.1', self.tunnel_port
        deadline = time.time() + 20
        while time.time() < deadline:
            try:
                socket.create_connection((self.connect_host, self.connect_port), timeout=1).close()
                return
            except OSError:
                time.sleep(0.5)
        raise RuntimeError('RDS로 가는 SSH 터널을 열지 못했다')

    def close(self):
        if self.tunnel:
            self.tunnel.terminate()

    def build(self):
        raise RuntimeError('AWS 대상은 빌드하지 않는다. 이미지를 EC2에 배포한 뒤 --skip-build로 실행한다')

    def connect(self):
        import pymysql
        return pymysql.connect(host=self.connect_host, port=self.connect_port, user=self.env['AWS_DB_USER'],
                               password=self.env['AWS_DB_PASSWORD'], database=self.env['AWS_DB_NAME'],
                               client_flag=pymysql.constants.CLIENT.MULTI_STATEMENTS, autocommit=True)

    def mysql(self, sql):
        connection = self.connect()
        try:
            with connection.cursor() as cursor:
                cursor.execute(sql)
                rows = cursor.fetchall()
                while cursor.nextset():
                    rows = cursor.fetchall() or rows
            return [['NULL' if value is None else str(value) for value in row] for row in rows or []]
        finally:
            connection.close()

    def reset_database_and_app(self):
        self.mysql((ROOT / 'src' / 'main' / 'resources' / 'schema.sql').read_text(encoding='utf-8'))
        self.mysql((PERF / 'seed.sql').read_text(encoding='utf-8'))
        self.remote(f'docker restart {self.app_container}')

    def access_log(self):
        return self.remote(f'docker exec {self.app_container} cat {ACCESS_LOG}').splitlines()

    def run_k6(self, script, run_dir, env):
        env = {**env, 'JWT_SECRET': self.env['AWS_JWT_SECRET']}
        if not self.k6_host:
            return run_local_k6(script, run_dir, {**env, 'BASE_URL': self.app_url})
        command = ' '.join(shlex.quote(part) for part in k6_command(script, '/tmp/k6.csv', {**env, 'BASE_URL': self.k6_app_url}))
        environment = ' '.join(f'{key}={value}' for key, value in K6_ENV.items())
        code = subprocess.run(self.ssh_base(self.k6_host) + [f'cd k6 && rm -f /tmp/k6.csv && {environment} {command} > /tmp/k6.log 2>&1'],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode
        remote = self.login(self.k6_host)
        sh(['scp', '-q', *self.ssh_options(), f'{remote}:/tmp/k6.csv', str(run_dir / 'k6.csv')])
        sh(['scp', '-q', *self.ssh_options(), f'{remote}:/tmp/k6.log', str(run_dir / f'{Path(script).stem}.log')])
        return code

    def sample_resources(self, metric):
        usage = metric('process.cpu.usage').get('VALUE')
        return {} if usage is None else {'app_cpu_pct': usage * 100}

    def cloud_metrics(self, started, ended):
        try:
            import boto3
        except ImportError:
            return {}
        session = boto3.Session(profile_name=self.env.get('AWS_PROFILE') or None, region_name=self.env.get('AWS_REGION', 'ap-northeast-2'))
        cloudwatch = session.client('cloudwatch')
        start = datetime.fromtimestamp(started, timezone.utc) - timedelta(minutes=5)
        end = datetime.fromtimestamp(ended, timezone.utc) + timedelta(minutes=1)
        result = {}
        targets = [('ec2', 'AWS/EC2', 'InstanceId', self.env.get('AWS_EC2_INSTANCE_ID')),
                   ('rds', 'AWS/RDS', 'DBInstanceIdentifier', self.env.get('AWS_RDS_INSTANCE_ID'))]
        for prefix, namespace, dimension, value in targets:
            if not value:
                continue
            for metric_name, statistic, key in (('CPUCreditBalance', 'Minimum', 'cpu_credit_balance'),
                                                ('CPUUtilization', 'Maximum', 'cloudwatch_cpu_max_pct')):
                points = cloudwatch.get_metric_statistics(Namespace=namespace, MetricName=metric_name,
                                                          Dimensions=[{'Name': dimension, 'Value': value}],
                                                          StartTime=start, EndTime=end, Period=60,
                                                          Statistics=[statistic])['Datapoints']
                if points:
                    result[f'{prefix}_{key}'] = min(p[statistic] for p in points) if statistic == 'Minimum' \
                        else max(p[statistic] for p in points)
        return result


def create(name):
    return AwsTarget() if name == 'aws' else LocalTarget()
