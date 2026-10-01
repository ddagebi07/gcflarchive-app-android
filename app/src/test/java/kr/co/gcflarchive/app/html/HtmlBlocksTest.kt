package kr.co.gcflarchive.app.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlBlocksTest {
    private val base = "https://www.gcfl.or.kr/board/"

    @Test
    fun splitsImagesOutOfParagraphs() {
        val blocks = HtmlBlocks.parse("<p>안내<b>문</b></p><p><img src='/a.png' alt='포스터'></p><p>끝</p><p>&nbsp;</p>", base)
        assertEquals(3, blocks.size)
        assertEquals("<p>안내<b>문</b></p>", (blocks[0] as HtmlBlock.Text).html)
        assertEquals(HtmlBlock.Image("https://www.gcfl.or.kr/a.png", "포스터"), blocks[1])
        assertTrue((blocks[2] as HtmlBlock.Text).html.contains("끝"))
    }

    @Test
    fun tablesKeepCellsAndColspan() {
        val blocks = HtmlBlocks.parse("<table><tr><th colspan=2>일정</th></tr><tr><td>1일</td><td><img alt='x' src='y'>개학</td></tr></table>", base)
        val table = blocks.single() as HtmlBlock.Table
        assertEquals(2, table.rows.size)
        assertTrue(table.rows[0][0].header)
        assertEquals(2, table.rows[0][0].colspan)
        assertEquals("x개학", table.rows[1][1].html)
    }

    @Test
    fun embedsAndScriptsAndColors() {
        val blocks = HtmlBlocks.parse(
            "<script>alert(1)</script><p style='color:#000;font-weight:bold'>t</p><iframe src='https://www.youtube.com/embed/X'></iframe>",
            base, stripColors = true,
        )
        assertEquals(2, blocks.size)
        val text = (blocks[0] as HtmlBlock.Text).html
        assertFalse(text.contains("color"))
        assertTrue(text.contains("font-weight"))
        assertEquals(HtmlBlock.Embed("https://www.youtube.com/embed/X"), blocks[1])
    }

    @Test
    fun emptyBodies() {
        assertTrue(HtmlBlocks.parse("", base).isEmpty())
        assertTrue(HtmlBlocks.parse("<p>&nbsp;</p><br>", base).isEmpty())
    }
}
