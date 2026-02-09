import java.io.File
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.bytedeco.opencv.global.opencv_imgproc._
import org.bytedeco.opencv.opencv_core._

@main def runMasking(): Unit = {
  val inputDir = new File("input")
  FileUtil.prepareTestImages(inputDir)

  val outputDir = new File("output")
  FileUtil.ensureDirExists(outputDir)

  val files = FileUtil.listJpgFiles(inputDir)
  println(s"処理開始: ${files.size} 枚")

  val ocrService = new OcrService("tessdata")

  // 並列処理開始
  val tasks = files.map { file =>
    Future {
      val api = ocrService.createApi()

      try {
        val sourceImage = ImageProcessor.readImage(file)

        // OCR用の前処理画像を作成
        // ここでは単純なグレースケール化と二値化を行う
        val gray = new Mat()
        cvtColor(sourceImage, gray, COLOR_BGR2GRAY)

        // ノイズ除去
        GaussianBlur(gray, gray, new Size(3, 3), 0)

        // 二値化（白背景・黒文字を想定して THRESH_BINARY）
        // Tesseractは白背景・黒文字を好むため、THRESH_BINARY_INVではなくTHRESH_BINARYを使用
        val binary = new Mat()
        threshold(gray, binary, 0, 255, THRESH_BINARY | THRESH_OTSU)

        // 画像全体から数字の領域を取得
        val digitRegions = ocrService.getDigitRegions(api, binary)

        println(s"File: ${file.getName}, Digit Regions: ${digitRegions.size}")

        digitRegions.foreach { rect =>
          // マスキング
          ImageProcessor.maskRegion(sourceImage, rect)
          println(s"  Masked digit at ${rect.x},${rect.y}")
        }

        val outFile = new File(outputDir, s"masked_${file.getName}")
        ImageProcessor.saveImage(outFile, sourceImage)
        println(s"完了: ${file.getName}")

      } finally {
        api.End()
        api.close()
      }
    }
  }

  Await.result(Future.sequence(tasks), 10.minutes)
  println("全てのマスキングが完了しました。")

  // 検証処理を実行
  verifyMasking()
}

def verifyMasking(): Unit = {
  println("\n--- マスキング結果の検証 ---")
  val outputDir = new File("output")
  if (!outputDir.exists()) return

  val files = Option(outputDir.listFiles()).map(_.filter(_.getName.startsWith("masked_")).toList).getOrElse(List.empty)
  if (files.isEmpty) {
    println("検証対象のファイルが見つかりません。")
    return
  }

  val ocrService = new OcrService("tessdata")
  val api = ocrService.createApi()

  try {
    files.foreach { file =>
      val src = ImageProcessor.readImage(file)

      // 検証用前処理: グレースケール化して二値化
      val gray = new Mat()
      cvtColor(src, gray, COLOR_BGR2GRAY)
      val binary = new Mat()
      threshold(gray, binary, 0, 255, THRESH_BINARY | THRESH_OTSU)

      // 画像全体をOCRにかける
      val text = ocrService.recognizeText(api, binary)

      // 数字が含まれているかチェック
      if (text.matches(".*\\d.*")) {
        // 数字が見つかった場合、どの数字が見つかったか表示
        val digits = text.filter(_.isDigit)
        println(s"[警告] ${file.getName} から数字が検出されました: '$digits' (全文: '$text')")
      } else {
        println(s"[OK] ${file.getName} から数字は検出されませんでした。")
      }
    }
  } finally {
    api.End()
    api.close()
  }
}