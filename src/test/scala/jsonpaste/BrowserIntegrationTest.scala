package jsonpaste

import kyo.*
import kyo.test.*

import java.io.File
import java.nio.file.{Files, Paths}

/** End-to-end coverage that the other suites deliberately skip.
  *
  * `JsonTest` pins the pure `JsonFormatter`, and `InputBindingTest` inspects the
  * static `WebApp.view` tree — neither starts a server or exercises the reactive
  * round trip. This suite boots the real `WebApp` on an ephemeral port and drives
  * it with a headless Chrome via kyo-browser, so the full path is covered:
  * keystroke -> `onInput` -> server re-render -> WebSocket diff -> DOM update.
  *
  * Chrome resolution: if a system Chrome/Chromium is available (via the
  * `KYO_BROWSER_CHROME` env override or on `PATH`), the test launches it through
  * a `LaunchConfig`; otherwise it falls back to kyo-browser's auto-downloaded
  * `chrome-headless-shell`. The system-Chrome path is the documented escape hatch
  * for environments where the bundled download can't run (e.g. NixOS, where the
  * downloaded binary's dynamic libraries don't resolve).
  *
  * Note: kyo-test's no-assertion enforcement counts only its own `assert` family,
  * not `Browser.assert*`. Each leaf therefore reads page state back through
  * `Browser.waitForText` and finishes with a kyo-test `assert`, which both drives
  * the browser and satisfies the enforcement.
  */
class BrowserIntegrationTest extends Test[Any]:

    /** A runnable Chrome/Chromium binary: `KYO_BROWSER_CHROME` if set, else the
      * first known name found on `PATH`. `None` means "let kyo-browser download". */
    private def findSystemChrome(): Option[String] =
        def onPath(name: String): Option[String] =
            sys.env.getOrElse("PATH", "").split(File.pathSeparatorChar).iterator
                .map(dir => Paths.get(dir, name))
                .find(Files.isExecutable)
                .map(_.toString)
        sys.env.get("KYO_BROWSER_CHROME").filter(_.nonEmpty).orElse:
            List("google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "chrome")
                .iterator.flatMap(onPath).nextOption()
    end findSystemChrome

    /** Run a Browser computation against a system Chrome when one is available,
      * otherwise against the auto-downloaded Chrome-for-Testing build. */
    private def runBrowser[A](body: A < (Browser & Abort[BrowserReadException]))(
        using Frame
    ): A < (Async & Abort[BrowserReadException | BrowserSetupException]) =
        findSystemChrome() match
            case Some(path) => Browser.run(Browser.LaunchConfig.chrome(path))(body)
            case None       => Browser.run(body)

    /** Boot WebApp on an ephemeral port and run `f` against its base URL. The
      * server is scope-managed, so it is torn down when the leaf ends. */
    private def withApp[A, S](f: String => A < S)(
        using Frame
    ): A < (S & Async & Scope & Abort[HttpBindException]) =
        val page: UI < Async = Signal.initRef("").map(WebApp.view)
        for
            handlers <- UI.runHandlers("/")(page)
            server   <- HttpServer.init(0, "127.0.0.1")(handlers*)
            result   <- f(s"http://127.0.0.1:${server.port}/")
        yield result
    end withApp

    "the live app pretty-prints pasted JSON in a real browser".slow.timeout(180.seconds) in {
        withApp { url =>
            runBrowser {
                for
                    _      <- Browser.goto(url)
                    _      <- Browser.fill(Browser.Selector.id("json"), """{"b":2,"a":1}""")
                    output <- Browser.waitForText(Browser.Selector.id("output"), _.contains("\"a\": 1"))
                yield output
            }
        }.map { output =>
            // The pure formatter's two-space indentation, observed through the
            // full server-push pipeline rather than by calling it directly.
            assert(output.contains("\"a\": 1"), "formatted output should contain the pretty-printed key")
            assert(output.contains("\"b\": 2"), "formatted output should preserve every key")
        }
    }

    "the live app reports invalid JSON in a real browser".slow.timeout(180.seconds) in {
        withApp { url =>
            runBrowser {
                for
                    _       <- Browser.goto(url)
                    _       <- Browser.fill(Browser.Selector.id("json"), "{ not json")
                    message <- Browser.waitForText(Browser.Selector.id("output"), _.nonEmpty)
                yield message
            }
        }.map { message =>
            assert(message.nonEmpty, "an invalid paste should surface a parse-error message in #output")
        }
    }

end BrowserIntegrationTest
