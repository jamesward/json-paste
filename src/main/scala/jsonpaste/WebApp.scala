package jsonpaste

import kyo.*
import kyo.Style.*
import kyo.UI.*

/** JSON Paste as a Kyo server-push web app.
  *
  * A single `SignalRef[String]` is two-way bound to a textarea. Every keystroke
  * is sent to the server, which re-derives the formatted output and pushes the
  * DOM diff back over the WebSocket, so the pretty-printed JSON (or the parse
  * error) updates live with no client-side code.
  *
  * The reactive surface is thin on purpose: all the real work is the pure,
  * exhaustively-tested `Json.format`, called inside the render body.
  */
object WebApp extends KyoApp:

    private val pageStyle =
        Style.padding(24.px).fontFamily(FontFamily.SansSerif).gap(12.px).maxWidth(960.px)

    private val subtitle = Style.color(Color.gray).fontSize(14.px)

    private val editorStyle =
        Style.width(100.pct).height(300.px).padding(10.px)
            .fontFamily(FontFamily.Monospace).fontSize(13.px)
            .rounded(8.px).border(1.px, Color.slate)

    private val outputBase =
        Style.width(100.pct).minHeight(120.px).padding(12.px)
            .fontFamily(FontFamily.Monospace).fontSize(13.px)
            .rounded(8.px).border(1.px, Color.slate)

    private val okStyle    = outputBase.bg(Color.white)
    private val errorStyle = outputBase.color(Color.red)

    private def ui: UI < Async =
        for input <- Signal.initRef("")
        yield UI.main.style(pageStyle)(
            h1("JSON Paste"),
            p("Paste JSON below; it is validated and pretty-printed live.").style(subtitle),
            textarea.id("json").placeholder("Paste JSON here…").value(input).style(editorStyle),
            input.render { raw =>
                if raw.trim.isEmpty then
                    p("Waiting for input…").id("status").style(subtitle)
                else
                    JsonFormatter.format(raw) match
                        case FormatResult.Formatted(pretty) =>
                            pre(pretty).id("output").style(okStyle)
                        case FormatResult.Invalid(message) =>
                            pre(message).id("output").style(errorStyle)
            }
        )

    run {
        for
            port     <- Sync.defer(sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(8080))
            handlers <- UI.runHandlers("/")(ui)
            server   <- HttpServer.init(port, "0.0.0.0")(handlers*)
            _        <- Console.printLine(s"JSON Paste listening on http://0.0.0.0:${server.port}/")
            _        <- server.await
        yield ()
    }

end WebApp
