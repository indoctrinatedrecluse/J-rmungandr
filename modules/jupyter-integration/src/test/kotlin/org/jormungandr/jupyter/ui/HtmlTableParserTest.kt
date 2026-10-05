package org.jormungandr.jupyter.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HtmlTableParserTest {

    @Test
    fun `test detects valid html table`() {
        val html = "<div><table class='dataframe'><tr><th>A</th></tr><tr><td>1</td></tr></table></div>"
        assertTrue(HtmlTableParser.isHtmlTable(html))
        assertFalse(HtmlTableParser.isHtmlTable("<div><p>No table here</p></div>"))
    }

    @Test
    fun `test parses standard pandas html table into columns and rows`() {
        val html = """
            <table border="1" class="dataframe">
              <thead>
                <tr style="text-align: right;">
                  <th></th>
                  <th>name</th>
                  <th>age</th>
                  <th>score</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <th>0</th>
                  <td>Alice</td>
                  <td>30</td>
                  <td>95.5</td>
                </tr>
                <tr>
                  <th>1</th>
                  <td>Bob</td>
                  <td>25</td>
                  <td>88.0</td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()

        val parsed = HtmlTableParser.parse(html)
        assertNotNull(parsed)
        parsed!!

        assertEquals(listOf("index", "name", "age", "score"), parsed.headers)
        assertEquals(2, parsed.rows.size)
        assertEquals(listOf("0", "Alice", "30", "95.5"), parsed.rows[0])
        assertEquals(listOf("1", "Bob", "25", "88.0"), parsed.rows[1])

        val csv = parsed.toCsv()
        assertTrue(csv.contains("index,name,age,score"))
        assertTrue(csv.contains("0,Alice,30,95.5"))
        assertTrue(csv.contains("1,Bob,25,88.0"))
    }

    @Test
    fun `test parses html table with escaping and tags`() {
        val html = """
            <table>
              <tr><th>City</th><th>Description</th></tr>
              <tr><td>New York</td><td>The &quot;Big Apple&quot;, NY &amp; NJ</td></tr>
              <tr><td>London</td><td>Capital of the <b>UK</b></td></tr>
            </table>
        """.trimIndent()

        val parsed = HtmlTableParser.parse(html)
        assertNotNull(parsed)
        parsed!!

        assertEquals(listOf("City", "Description"), parsed.headers)
        assertEquals(2, parsed.rows.size)
        assertEquals("New York", parsed.rows[0][0])
        assertEquals("The \"Big Apple\", NY & NJ", parsed.rows[0][1])
        assertEquals("London", parsed.rows[1][0])
        assertEquals("Capital of the UK", parsed.rows[1][1])

        val csv = parsed.toCsv()
        assertTrue(csv.contains("\"The \"\"Big Apple\"\", NY & NJ\""))
    }

    @Test
    fun `test empty or invalid table returns null`() {
        assertNull(HtmlTableParser.parse("<table></table>"))
        assertNull(HtmlTableParser.parse("Just plain text"))
    }
}
