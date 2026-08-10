package io.github.chenyouxin8.chenaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 终端命令执行工具（安全版）
 *
 * 白名单策略：只允许执行预定义的、安全的命令。
 * 所有命令经过以下检查：
 * 1. 命令必须在白名单中
 * 2. 禁止使用 && || | ; & 等 Shell 连接符
 * 3. 禁止重定向 > < 和管道 |
 * 4. 每条命令有 30 秒超时
 *
 * 允许的命令：
 * - Windows 系统命令：ipconfig, dir, ping, tracert, nslookup, netstat, systeminfo, tasklist, hostname
 * - 编译运行：javac, java, mvn, gradle, npm, node, python, python3
 * - Git：git status, git log, git branch, git diff, git pull, git push
 * - 文件操作：type（类似 cat），del, copy, move
 * - 网络：curl, wget
 */
public class TerminalOperationTool {

    /**
     * 预编译正则，禁止连接符/重定向
     */
    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
            "[&|;`<>$()\\[\\]{}\\\\\"\']|>>|<<|2>|\\|\\s*\\w"
    );

    /**
     * 命令白名单（命令名 -> 允许的参数正则）
     */
    @SuppressWarnings("deprecation")
    private static final List<CommandRule> ALLOWED_COMMANDS = List.of(
            // === Windows 系统命令 ===
            new CommandRule("ipconfig", Pattern.compile("^(/all|/release|/renew|/flushdns)?\\s*$")),
            new CommandRule("dir", Pattern.compile("^[^\"/:*?<>|]+(\\s+/[a-z]+)?\\s*$")),
            new CommandRule("ping", Pattern.compile("^[-:\\\\w.]+\\s+(-n\\s+\\d+\\s+)?(-w\\s+\\d+\\s+)?$")),
            new CommandRule("tracert", Pattern.compile("^[-:\\\\w.]+\\s*$")),
            new CommandRule("nslookup", Pattern.compile("^[-:\\\\w.]*\\s*$")),
            new CommandRule("netstat", Pattern.compile("^(-an|-ao|-ano|-r)?\\s*$")),
            new CommandRule("systeminfo", Pattern.compile("^\\s*$")),
            new CommandRule("tasklist", Pattern.compile("^(/fi\\s+\"[^\"]*\")?\\s*$")),

            // === 编译/运行 ===
            new CommandRule("javac", Pattern.compile("^[^\"*/:?<>|\\\\]+\\.java\\s*$")),
            new CommandRule("java", Pattern.compile("^[-:\\\\w.]+\\s*$")),
            new CommandRule("mvn", Pattern.compile("^(compile|test|package|clean|verify)(-D[\\\\w=.]+)*\\s*$")),
            new CommandRule("npm", Pattern.compile("^(install|run\\s+(dev|build|start|test)|list)(-[\\\\w]+)*\\s*$")),
            new CommandRule("node", Pattern.compile("^[-:\\\\w/.]+\\.js\\s*$")),
            new CommandRule("python", Pattern.compile("^[-:\\\\w/.]+\\.py\\s*$")),
            new CommandRule("python3", Pattern.compile("^[-:\\\\w/.]+\\.py\\s*$")),

            // === Git ===
            new CommandRule("git", Pattern.compile("^(status|log|branch|diff|pull|push|clone|fetch|stash)(-v)?\\s*$")),

            // === 文件操作 ===
            new CommandRule("type", Pattern.compile("^[^\"/:*?<>|\\\\]+\\.\\w+\\s*$")),
            new CommandRule("curl", Pattern.compile("^(-[\\\\w]+)\\s+(https?://[-:\\\\w./=?&#]+)\\s*$")),

            // === hostname ===
            new CommandRule("hostname", Pattern.compile("^\\s*$"))
    );

    private record CommandRule(String name, Pattern argsPattern) {}

    @Tool(description = "Execute a safe whitelisted command in the terminal")
    public String executeTerminalCommand(
            @ToolParam(description = "Command to execute (must be in whitelist)") String command) {

        // 0. 基础合法性检查
        if (command == null || command.isBlank()) {
            return "Error: Command cannot be empty";
        }

        String trimmed = command.trim();

        // 1. 检查是否包含禁止字符
        if (FORBIDDEN_PATTERN.matcher(trimmed).find()) {
            return "Error: Command contains forbidden characters (shell operators, redirects, or quotes are not allowed)";
        }

        // 2. 提取命令名
        String[] parts = trimmed.split("\\s+");
        String cmdName = parts[0].toLowerCase();

        // 3. 白名单匹配
        CommandRule matchedRule = ALLOWED_COMMANDS.stream()
                .filter(r -> r.name().equals(cmdName))
                .findFirst()
                .orElse(null);

        if (matchedRule == null) {
            return "Error: Command '" + cmdName + "' is not in the whitelist. Allowed commands: " +
                    ALLOWED_COMMANDS.stream().map(r -> r.name).reduce((a, b) -> a + ", " + b).orElse("");
        }

        // 4. 参数格式检查
        String args = parts.length > 1 ? trimmed.substring(cmdName.length()) : "";
        if (!matchedRule.argsPattern().matcher(args).matches()) {
            return "Error: Invalid arguments for command '" + cmdName + "'";
        }

        // 5. 执行命令
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", trimmed);
            builder.redirectErrorStream(true);
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);

            Process process = builder.start();

            // 30 秒超时
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Error: Command timed out after 30 seconds";
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 200) {
                    output.append(line).append("\n");
                    lineCount++;
                }
                if (lineCount >= 200) {
                    output.append("\n[Output truncated to 200 lines]\n");
                }
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                if (output.length() == 0) {
                    output.append("Command exited with code ").append(exitCode);
                }
            }
        } catch (IOException e) {
            return "Error executing command: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: Command execution was interrupted";
        }

        return output.toString();
    }
}