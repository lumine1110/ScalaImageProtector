import java.io.File
import java.awt.{Color, Font}
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

object FileUtil {
  def ensureDirExists(dir: File): Unit = {
    if (!dir.exists()) dir.mkdir()
  }

  def listJpgFiles(dir: File): List[File] = {
    if (dir.exists() && dir.isDirectory) {
      dir.listFiles().filter(_.getName.endsWith(".jpg")).toList
    } else {
      List.empty
    }
  }

  def prepareTestImages(dir: File): Unit = {
    ensureDirExists(dir)
    if (dir.listFiles() == null || dir.listFiles().isEmpty) {
      println("テスト用画像を生成します...")
      for (i <- 1 to 3) {
        val width = 400
        val height = 300
        val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()

        g.setColor(Color.WHITE)
        g.fillRect(0, 0, width, height)

        g.setColor(Color.BLACK)
        g.setFont(new Font("Serif", Font.BOLD, 48))
        g.drawString(s"Test $i", 50, 100)
        g.drawString(s"12345", 100, 200)

        g.dispose()
        ImageIO.write(image, "jpg", new File(dir, s"test_$i.jpg"))
      }
    }
  }
}