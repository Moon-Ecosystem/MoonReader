package com.example

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.example.ui.screens.fittedSize
import com.example.ui.screens.maxPan
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomPanTest {
  // A 1080x2400 phone showing a 1000x1500 manga page: fits the width, leaving bars above and below
  private val view = IntSize(1080, 2400)
  private val page = fittedSize(Size(1000f, 1500f), view)

  // Float maths: compare to within a hundredth of a pixel
  private fun assertNear(expected: Offset, actual: Offset) {
    assertEquals(expected.x, actual.x, 0.01f)
    assertEquals(expected.y, actual.y, 0.01f)
  }

  @Test
  fun `a page narrower than the screen fits the width`() {
    assertEquals(1080f, page.width, 0.01f)
    assertEquals(1620f, page.height, 0.01f)
  }

  @Test
  fun `zoomed in, the letterbox bars can't be panned into`() {
    // At 1.2x the page is 1944 tall, still shorter than the 2400 view: no vertical pan at all
    assertEquals(0f, maxPan(1.2f, view, page).y, 0.01f)
    // At 2x it is 3240 tall: pan only until its top or bottom edge meets the view's, (3240 - 2400) / 2
    assertNear(Offset(540f, 420f), maxPan(2f, view, page))
  }

  @Test
  fun `a page filling the view keeps the old limits`() {
    // Webtoon rows are exactly the page's size, so this matches (scale - 1) * view / 2
    assertNear(Offset(540f, 1200f), maxPan(2f, view, Size(1080f, 2400f)))
  }

  @Test
  fun `before the image loads the whole view is used`() {
    assertEquals(Size(1080f, 2400f), fittedSize(Size.Unspecified, view))
  }
}
