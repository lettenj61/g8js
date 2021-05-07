package g8js

import utest._

object TemplateTests extends TestSuite {
  val ctx = Map(
    "hello"     -> "world",
    "developer" -> "me",
    "version"   -> "2.13",
    "home"      -> "/home/ubuntu"
  )

  val tests = Tests {
    test("substitute") {
      Template.render("version is $version$", ctx) ==> "version is 2.13"
    }

    test("escape") {
      val expected = "escaped: $OK, processed: world"
      val got      = Template.render("escaped: $$OK, processed: $hello$", ctx)

      assert(expected == got)
    }

    test("escapeAndBrace") {
      val expected = "id: ${home}"
      val got      = Template.render("id: $${home}", ctx)

      assert(expected == got)
    }
  }
}
