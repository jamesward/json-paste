package jsonpaste

import kyo.*
import kyo.test.*

import jsonpaste.{JsonFormatter, FormatResult}

/** Unit tests for the pure formatter. Parsing is Kyo's; these pin down the
  * behavior this project owns: indented output, validity, and the documented
  * number-normalization tradeoff inherited from Kyo's reader.
  */
class JsonTest extends Test[Any]:

    private def isValid(raw: String): Boolean =
        JsonFormatter.parse(raw) match
            case Present(_) => true
            case Absent     => false

    private def formatted(raw: String)(using AssertScope): String =
        JsonFormatter.format(raw) match
            case FormatResult.Formatted(pretty) => pretty
            case FormatResult.Invalid(message)  => fail(s"expected valid JSON, got: $message")

    "parse" - {
        "accepts well-formed JSON" in {
            assert(isValid("null"))
            assert(isValid("true"))
            assert(isValid("\"hi\""))
            assert(isValid("42"))
            assert(isValid("[1,2,3]"))
            assert(isValid("""{"a":1,"b":[true,null]}"""))
            assert(isValid("  [ 1 , 2 ]  "))
        }

        "rejects malformed input" in {
            assert(!isValid(""))
            assert(!isValid("{"))
            assert(!isValid("[1,]"))
            assert(!isValid("nope"))
        }
    }

    "format" - {
        "pretty-prints an object with two-space indentation" in {
            val expected =
                """{
                  |  "a": 1,
                  |  "b": [
                  |    true,
                  |    null
                  |  ]
                  |}""".stripMargin
            assert(formatted("""{"a":1,"b":[true,null]}""") == expected)
        }

        "renders empty containers compactly" in {
            assert(formatted("[]") == "[]")
            assert(formatted("{}") == "{}")
        }

        "re-escapes string values" in {
            assert(formatted("\"a\\nb\"") == "\"a\\nb\"")
            assert(formatted("\"\\u0041\"") == "\"A\"")
        }

        "normalizes numbers (Kyo reader tradeoff: exact lexeme not preserved)" in {
            assert(formatted("42") == "42")
            assert(formatted("1.50") == "1.5")
            assert(formatted("3.14") == "3.14")
        }

        "is idempotent: formatting formatted output changes nothing" in {
            val once = formatted("""{"name":"Ada","tags":["x","y"],"n":42,"ok":true,"nil":null}""")
            assert(formatted(once) == once)
        }

        "reports invalid input with a message" in {
            JsonFormatter.format("{") match
                case FormatResult.Invalid(message) => assert(message.nonEmpty)
                case FormatResult.Formatted(_)     => fail("expected invalid result")
        }

        "degrades gracefully on integers larger than Long (does not throw)" in {
            JsonFormatter.format("123456789012345678901234567890") match
                case FormatResult.Invalid(message) => assert(message.nonEmpty)
                case FormatResult.Formatted(pretty) => assert(pretty.nonEmpty)
        }
    }

end JsonTest
