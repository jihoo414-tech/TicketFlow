// UserPromptSubmit 훅: 사용자가 입력한 프롬프트를 prompt.md 끝에 추가한다.
const fs = require("fs");
const path = require("path");

let input = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => (input += chunk));
process.stdin.on("end", () => {
  const { prompt, cwd } = JSON.parse(input);
  if (!prompt || !prompt.trim()) return;

  const root = process.env.CLAUDE_PROJECT_DIR || cwd;
  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  const stamp = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`;

  fs.appendFileSync(path.join(root, "prompt.md"), `## ${stamp}\n\n${prompt.trim()}\n\n`);
});
