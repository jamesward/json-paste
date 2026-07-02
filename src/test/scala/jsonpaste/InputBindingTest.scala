package jsonpaste

import kyo.*
import kyo.test.*

/** Regression guard for "fast typing loses keystrokes".
  *
  * Root cause (verified against kyo-ui 1.0.0-RC5): binding the textarea two-way
  * with `.value(SignalRef)` makes it a server-controlled input — "ref changes
  * update the input" (UI.scala). In this server-push app the server applies
  * inbound events serially (`ws.stream.foreach` in UIServer), re-renders, and
  * pushes the value back into the textarea. Under fast typing each keystroke
  * round-trips, and the echoed (now stale) value overwrites characters typed in
  * the meantime, so keystrokes are dropped.
  *
  * The literal timing symptom only manifests in a real browser, but the
  * invariant that prevents it is deterministically checkable: the field the user
  * types into must NOT be server-controlled. The view reads edits via `onInput`
  * and never binds the value back, so no textarea carries a `Bound.Ref` value.
  */
class InputBindingTest extends Test[Any]:

    /** All textarea nodes reachable through static element children. */
    private def textareas(ui: UI): List[UI.Ast.Textarea] =
        ui match
            case t: UI.Ast.Textarea => List(t)
            case e: UI.Ast.Element  => e.children.toList.flatMap(textareas)
            case _                  => Nil

    private def isServerControlled(t: UI.Ast.Textarea): Boolean =
        t.value match
            case Present(UI.Bound.Ref(_)) => true
            case _                        => false

    "the input textarea is uncontrolled so fast typing is not clobbered" in {
        for input <- Signal.initRef("")
        yield
            val inputs = textareas(WebApp.view(input))
            assert(inputs.nonEmpty, "expected a textarea in the view")
            assert(
                !inputs.exists(isServerControlled),
                "the JSON textarea is server-controlled via .value(SignalRef); its value would be " +
                    "echoed back on every keystroke, dropping characters typed during the round-trip"
            )
    }

end InputBindingTest
