package jsonpaste

import kyo.*

/** The outcome of formatting pasted text: either a pretty-printed document or a
  * human-readable explanation of why the input was not valid JSON.
  */
enum FormatResult derives CanEqual:
    case Formatted(pretty: String)
    case Invalid(message: String)

/** Pretty-prints arbitrary JSON.
  *
  * Parsing is delegated to Kyo: `Json.decode[Structure.Value]` reads any JSON
  * shape into Kyo's universal value tree, so there is no bespoke parser to
  * maintain. The only thing Kyo does not provide is indented output — `Json.encode`
  * emits compact JSON — so this object supplies a pretty-printer over the tree.
  *
  * Caveat inherited from Kyo's reader: numbers are normalized into `Long` /
  * `Double` / `BigDecimal`, so the exact source lexeme is not preserved (for
  * example `1.50` renders as `1.5`), and an integer larger than `Long.MaxValue`
  * is reported as invalid rather than pretty-printed.
  */
object JsonFormatter:

    /** Parse arbitrary JSON into Kyo's universal value tree, or `Absent`. */
    def parse(raw: String): Maybe[Structure.Value] =
        Result.catching[Throwable](Json.decode[Structure.Value](raw))
            .toMaybe
            .flatMap(_.toMaybe)

    /** Parse and pretty-print pasted text. Total: a `String => FormatResult`. */
    def format(raw: String): FormatResult =
        Result.catching[Throwable](Json.decode[Structure.Value](raw)).foldError(
            decoded =>
                decoded.foldError(
                    value => FormatResult.Formatted(render(value)),
                    error => FormatResult.Invalid(describe(error.failureOrPanic))
                ),
            error => FormatResult.Invalid(describe(error.failureOrPanic))
        )

    private def describe(t: Throwable): String =
        Option(t.getMessage).getOrElse(t.toString)

    // ---- Pretty-printing --------------------------------------------------

    /** Render a value tree as indented JSON using two-space indentation. */
    def render(value: Structure.Value): String = render(value, indentWidth = 2)

    /** Render a value tree as indented JSON. */
    def render(value: Structure.Value, indentWidth: Int): String =
        def pad(depth: Int): String = " " * (indentWidth * depth)

        def obj(entries: List[(String, Structure.Value)], depth: Int): String =
            if entries.isEmpty then "{}"
            else
                val body =
                    entries
                        .map((k, v) => s"${pad(depth + 1)}$k: ${go(v, depth + 1)}")
                        .mkString(",\n")
                s"{\n$body\n${pad(depth)}}"

        def arr(elements: List[Structure.Value], depth: Int): String =
            if elements.isEmpty then "[]"
            else
                val body = elements.map(e => pad(depth + 1) + go(e, depth + 1)).mkString(",\n")
                s"[\n$body\n${pad(depth)}]"

        def go(v: Structure.Value, depth: Int): String =
            v match
                case Structure.Value.Null           => "null"
                case Structure.Value.Bool(b)        => if b then "true" else "false"
                case Structure.Value.Integer(l)     => l.toString
                case Structure.Value.Decimal(d)     => d.toString
                case Structure.Value.BigNum(bd)     => bd.toString
                case Structure.Value.Str(s)         => quote(s)
                case Structure.Value.Sequence(xs)   => arr(xs.toList, depth)
                case Structure.Value.Record(fields) =>
                    obj(fields.toList.map((k, v) => quote(k) -> v), depth)
                // The following two cannot arise from decoding raw JSON (objects
                // become Record, arrays become Sequence); handled for totality.
                case Structure.Value.MapEntries(entries) =>
                    obj(entries.toList.map((k, v) => keyText(k) -> v), depth)
                case Structure.Value.VariantCase(name, payload) =>
                    obj(List(quote(name) -> payload), depth)

        go(value, 0)
    end render

    /** Render a map key as a JSON string key. */
    private def keyText(key: Structure.Value): String =
        key match
            case Structure.Value.Str(s) => quote(s)
            case other                  => quote(render(other))

    /** Encode a string as a JSON string literal, escaping as required by RFC 8259. */
    private def quote(s: String): String =
        val escaped =
            s.iterator.map {
                case '"'          => "\\\""
                case '\\'         => "\\\\"
                case '\n'         => "\\n"
                case '\r'         => "\\r"
                case '\t'         => "\\t"
                case '\b'         => "\\b"
                case '\f'         => "\\f"
                case c if c < ' ' => f"\\u${c.toInt}%04x"
                case c            => c.toString
            }.mkString
        "\"" + escaped + "\""
    end quote

end JsonFormatter
