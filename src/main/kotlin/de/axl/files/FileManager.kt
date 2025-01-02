package de.axl.files

import de.axl.db.ExposedImport
import de.axl.db.ImportService
import de.axl.db.ImportType
import net.coobird.thumbnailator.Thumbnails
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.tools.imageio.ImageIOUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.ProcessBuilder.Redirect
import java.nio.file.Files
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

class FileManager(val dataPath: String, private val importService: ImportService) {

    fun createFolder(base: String, vararg path: String) {
        val p = Path(base, *path)
        if (!p.exists()) {
            logger.info("Creating directory ${p.pathString}")
            Files.createDirectory(p)
        }
    }

    suspend fun handleUpload(filename: String) {
        logger.info("Handling uploaded file $filename")
        val file = File("$dataPath/upload", filename)
        if (file.exists()) {
            val guid = UUID.randomUUID().toString()
            when (file.extension) {
                "pdf" -> handlePdfUpload(file, guid)
                "png", "jpg", "jpeg" -> handleImgUpload(file, guid)
                else -> logger.error("File $filename has unsupported filetype ${file.extension}")
            }
        } else {
            logger.error("File $filename does not exist")
        }
    }

    private suspend fun handlePdfUpload(file: File, guid: String) {
        logger.info("Detected PDF")
        val originalFilename = "$guid-original.${file.extension}"
        logger.info("Moving file to $originalFilename")
        val newPath = Files.move(file.toPath(), Path("$dataPath/docs/pdf", originalFilename))

        val ocrPdf = createSearchablePdf(newPath.toFile(), guid)

        val text = extractTextFromPdf(ocrPdf)

        createImagesFromPdf(ocrPdf)

        val page1Img = getImage(guid, 1)
        createThumbnails(page1Img)

        logger.info("Creating import for $guid")
        importService.create(ExposedImport(guid, originalFilename, ImportType.PDF, ocrPdf.name, text, "", null))
        logger.info("PDF import created")
    }

    private suspend fun handleImgUpload(file: File, guid: String) {
        logger.info("Detected Image (${file.extension})")
    }

    private fun createSearchablePdf(file: File, guid: String): File {
        logger.info("Creating searchable PDF")
        val dir = file.parentFile
        val name = file.name
        runCommand(dir, "ocrmypdf --rotate-pages --deskew --clean --skip-text --language deu+eng $name $guid.pdf")
        return File(dir, "$guid.pdf")
    }

    private fun createImagesFromPdf(file: File) {
        val document = Loader.loadPDF(file);
        val pdfRenderer = PDFRenderer(document);
        for (i in 0 until document.numberOfPages) {
            logger.info("Creating image from pdf page ${i + 1}")
            var img = pdfRenderer.renderImageWithDPI(i, 300F, ImageType.RGB);
            ImageIOUtil.writeImage(img, "${dataPath}/docs/img/${file.nameWithoutExtension}-${(i + 1).toString().padStart(4, '0')}.png", 300);
        }
        document.close();
    }

    private fun createThumbnails(file: File) {
        logger.info("Creating Thumbnail 128x128")
        Thumbnails.of(file).size(128, 128).outputFormat("png").toFile(File("${dataPath}/docs/thumb/${file.nameWithoutExtension}-128.png"))
        logger.info("Creating Thumbnail 256x256")
        Thumbnails.of(file).size(256, 256).outputFormat("png").toFile(File("${dataPath}/docs/thumb/${file.nameWithoutExtension}-256.png"))
        logger.info("Creating Thumbnail 512x512")
        Thumbnails.of(file).size(512, 512).outputFormat("png").toFile(File("${dataPath}/docs/thumb/${file.nameWithoutExtension}-512.png"))
        logger.info("Creating Thumbnail 128W")
        Thumbnails.of(file).width(128).outputFormat("png").toFile(File("${dataPath}/docs/thumb/${file.nameWithoutExtension}-128W.png"))
        logger.info("Creating Thumbnail 256W")
        Thumbnails.of(file).width(256).outputFormat("png").toFile(File("${dataPath}/docs/thumb/${file.nameWithoutExtension}-256W.png"))
        logger.info("Creating Thumbnail 512W")
        Thumbnails.of(file).width(512).outputFormat("png").toFile(File("${dataPath}/docs/thumb/${file.nameWithoutExtension}-512W.png"))
        logger.info("Creating Thumbnail 128H")
        Thumbnails.of(file).height(128).outputFormat("png").toFile(File("${dataPath}/docs/thumb/${file.nameWithoutExtension}-128H.png"))
        logger.info("Creating Thumbnail 256H")
        Thumbnails.of(file).height(256).outputFormat("png").toFile(File("${dataPath}/docs/thumb/${file.nameWithoutExtension}-256H.png"))
        logger.info("Creating Thumbnail 512H")
        Thumbnails.of(file).height(512).outputFormat("png").toFile(File("${dataPath}/docs/thumb/${file.nameWithoutExtension}-512H.png"))
    }

    private fun extractTextFromPdf(file: File): String {
        logger.info("Extracting text from pdf")
        val document = Loader.loadPDF(file);
        val text = PDFTextStripper().getText(document)
        return text.lines().filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun getImage(guid: String, page: Int): File {
        return File("$dataPath/docs/img/${guid}-${page.toString().padStart(4, '0')}.png")
    }

    private fun runCommand(workingDir: File, command: String) {
        ProcessBuilder(*command.split(" ").toTypedArray())
            .directory(workingDir)
            .redirectOutput(Redirect.INHERIT)
            .redirectError(Redirect.INHERIT)
            .start()
            .waitFor(10, TimeUnit.MINUTES)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.declaringClass)
    }
}