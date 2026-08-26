package com.anfas.core.data

import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the Koin contract that `dataModule` relies on to close the Room connection.
 *
 * `dataModule` declares the database as
 * `single<AnfasDatabase> { ... }.withOptions { onClose { it?.close() } }`, so that quitting closes
 * the connection without every platform launcher having to remember to. The realistic way for that
 * to break is not the database — it is this API being misused or changed under us. It was in fact
 * misused first time round: `single { } onClose { }` reads naturally and does not compile, because
 * in Koin 4.2 `onClose` is an extension on `BeanDefinition` reachable only inside `withOptions`.
 *
 * Deliberately an **isolated** `koinApplication` rather than `startKoin`/`stopKoin`: the global
 * context is process-wide, and the real `dataModule` resolves a real database path — on desktop
 * that is now the user's own Application Support directory, which a test must never touch.
 */
class KoinOnCloseContractTest {

    private class Closeable {
        var closed = false
    }

    @Test
    fun `closing a koin application invokes onClose for a resolved single`() {
        val probe = Closeable()
        val app = koinApplication {
            modules(
                module {
                    single { probe }.withOptions { onClose { it?.closed = true } }
                },
            )
        }

        // Resolve it: a single that was never asked for has no instance to close.
        app.koin.get<Closeable>()
        assertFalse(probe.closed, "closed before the application was closed")

        app.close()

        assertTrue(probe.closed, "onClose did not fire — the database would leak its connection")
    }

    /**
     * The nullable receiver in `onClose { it?.close() }` is not defensive noise: Koin hands the
     * callback null when there is no instance, which is exactly the never-resolved case.
     */
    @Test
    fun `a single that was never resolved does not report a spurious close`() {
        val probe = Closeable()
        val app = koinApplication {
            modules(
                module {
                    single { probe }.withOptions { onClose { it?.closed = true } }
                },
            )
        }

        app.close()

        assertFalse(probe.closed, "onClose ran for an instance that was never created")
    }
}
