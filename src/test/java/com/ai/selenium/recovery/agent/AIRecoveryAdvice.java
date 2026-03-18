package com.ai.selenium.recovery.agent;

import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;

/**
 * ByteBuddy Advice that intercepts methods annotated with {@code @AIRecover}.
 *
 * <p>This class is inlined into the target method's bytecode by ByteBuddy.
 * When the method throws, the advice attempts AI-powered recovery via Playwright MCP.
 * If recovery succeeds, the exception is suppressed and the method returns normally.
 */
public class AIRecoveryAdvice {

    @Advice.OnMethodExit(onThrowable = Exception.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.Origin Method method,
            @Advice.AllArguments Object[] args,
            @Advice.Thrown(readOnly = false) Exception thrown) {
        if (thrown != null) {
            boolean recovered = AIRecoveryHandler.attemptRecovery(method, args, thrown);
            if (recovered) {
                thrown = null;
            }
        }
    }
}
