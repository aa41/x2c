package dev.x2c.fixture.producer;

import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic, Android-free login rules shared by UI click handlers and class-JAR tests. */
public final class LoginLogic {
    public static final String DEMO_EMAIL = "demo@x2c.dev";
    public static final String DEMO_PASSWORD = "x2c2026";
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);

    private LoginLogic() {}

    public static Validation validate(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);
        String password = rawPassword == null ? "" : rawPassword;
        String emailError = email.isEmpty()
                ? "请输入邮箱地址"
                : EMAIL.matcher(email).matches() ? null : "请输入有效的邮箱地址";
        String passwordError = password.isEmpty()
                ? "请输入登录密码"
                : password.length() >= 6 ? null : "密码至少需要 6 位";
        return new Validation(email, password, emailError, passwordError);
    }

    public static Outcome authenticate(String rawEmail, String rawPassword) {
        Validation validation = validate(rawEmail, rawPassword);
        if (!validation.valid()) {
            return Outcome.INVALID_INPUT;
        }
        if (DEMO_EMAIL.equals(validation.email()) && DEMO_PASSWORD.equals(validation.password())) {
            return Outcome.SUCCESS;
        }
        return Outcome.INVALID_CREDENTIALS;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String runSelfTests() {
        int passed = 0;
        require(!validate("", "").valid(), "empty fields");
        passed++;
        require(validate("bad", "x2c2026").emailError() != null, "malformed email");
        passed++;
        require(validate(DEMO_EMAIL, "123").passwordError() != null, "short password");
        passed++;
        require(validate(" DEMO@X2C.DEV ", DEMO_PASSWORD).valid(), "normalization");
        passed++;
        require(authenticate(DEMO_EMAIL, "incorrect") == Outcome.INVALID_CREDENTIALS,
                "incorrect credentials");
        passed++;
        require(authenticate(DEMO_EMAIL, DEMO_PASSWORD) == Outcome.SUCCESS, "success");
        passed++;
        return "PASS login-logic cases=" + passed;
    }

    private static void require(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Login logic probe failed: " + name);
        }
    }

    public enum Outcome {
        INVALID_INPUT,
        INVALID_CREDENTIALS,
        SUCCESS
    }

    public record Validation(
            String email,
            String password,
            String emailError,
            String passwordError) {
        public boolean valid() {
            return emailError == null && passwordError == null;
        }
    }
}
