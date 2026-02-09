import org.bytedeco.tesseract.TessBaseAPI
import org.bytedeco.tesseract.ResultIterator
import org.bytedeco.tesseract.global.tesseract._
import org.bytedeco.opencv.opencv_core.{Mat, Rect}
import org.bytedeco.javacpp.IntPointer
import java.io.{File, FileOutputStream}
import java.net.URI
import java.nio.channels.Channels
import scala.collection.mutable.ListBuffer

class OcrService(tessDataPath: String) {

  def createApi(): TessBaseAPI = {
    prepareTessData(tessDataPath)
    val api = new TessBaseAPI()
    if (api.Init(tessDataPath, "eng") != 0) {
      throw new Exception("Tesseractの初期化に失敗しました")
    }
    // ページセグメンテーションモードを自動（PSM_AUTO）に変更
    api.SetPageSegMode(PSM_AUTO)
    api
  }

  def recognizeText(api: TessBaseAPI, image: Mat): String = {
    api.SetImage(image.data().asByteBuffer(), image.cols(), image.rows(), 1, image.step().toInt)
    val textPtr = api.GetUTF8Text()
    val text = if (textPtr != null) textPtr.getString().trim() else ""
    if (textPtr != null) textPtr.deallocate()
    text
  }

  def getDigitRegions(api: TessBaseAPI, image: Mat): List[Rect] = {
    api.SetImage(image.data().asByteBuffer(), image.cols(), image.rows(), 1, image.step().toInt)

    // 認識実行
    if (api.Recognize(null) != 0) {
      return List.empty
    }

    val iterator = api.GetIterator()
    val regions = ListBuffer[Rect]()

    if (iterator != null) {
      try {
        val level = RIL_SYMBOL // 文字単位でイテレート

        // IntPointerの準備 (1Lを渡して曖昧さを回避)
        val left = new IntPointer(1L)
        val top = new IntPointer(1L)
        val right = new IntPointer(1L)
        val bottom = new IntPointer(1L)

        var hasNext = true
        while (hasNext) {
          val textPtr = iterator.GetUTF8Text(level)
          val text = if (textPtr != null) textPtr.getString().trim() else ""
          if (textPtr != null) textPtr.deallocate()

          // 数字であれば領域を取得
          if (text.nonEmpty && text.matches("\\d")) {
            if (iterator.BoundingBox(level, left, top, right, bottom)) {
              val x = left.get()
              val y = top.get()
              val w = right.get() - x
              val h = bottom.get() - y
              regions += new Rect(x, y, w, h)
            }
          }
          hasNext = iterator.Next(level)
        }

        // IntPointerの解放
        left.close()
        top.close()
        right.close()
        bottom.close()

      } finally {
        // iteratorはdeleteする必要があるが、JavaCVではGC任せか？
        // 明示的なcloseメソッドはないが、Pointerなので通常はそのまま
      }
    }
    regions.toList
  }

  private def prepareTessData(path: String): Unit = {
    val dir = new File(path)
    if (!dir.exists()) dir.mkdir()

    val trainedData = new File(dir, "eng.traineddata")
    if (!trainedData.exists()) {
      println("Tesseract学習データ(eng.traineddata)をダウンロード中...")
      val url = new URI("https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata").toURL
      val rbc = Channels.newChannel(url.openStream())
      val fos = new FileOutputStream(trainedData)
      fos.getChannel.transferFrom(rbc, 0, Long.MaxValue)
      fos.close()
      rbc.close()
      println("ダウンロード完了")
    }
  }
}