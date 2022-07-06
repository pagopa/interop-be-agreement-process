package it.pagopa.interop.agreementprocess.service.impl

import com.openhtmltopdf.util.XRLog
import it.pagopa.interop.agreementprocess.service.PDFCreator
import it.pagopa.interop.commons.files.model.PDFConfiguration
import it.pagopa.interop.commons.files.service.PDFManager

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.Future
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.util.Try

object PDFCreatorImpl extends PDFCreator with PDFManager {

  // Suppressing openhtmltopdf log
  XRLog.listRegisteredLoggers.asScala.foreach((logger: String) =>
    XRLog.setLevel(logger, java.util.logging.Level.SEVERE)
  )
  private[this] val pdfConfigs: PDFConfiguration = PDFConfiguration(resourcesBaseUrl = Some("/agreementTemplate/"))
  private[this] val printedDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  override def create(template: String, eservice: String, producer: String, consumer: String): Future[Array[Byte]] =
    Future.fromTry {
      def toByteArray: ByteArrayOutputStream => Try[Array[Byte]] =
        getPDF[ByteArrayOutputStream, Array[Byte]](template, setupData(eservice, producer, consumer), pdfConfigs)(
          _.toByteArray
        )
      toByteArray(new ByteArrayOutputStream())
    }

  private def setupData(eservice: String, producer: String, consumer: String): Map[String, String] =
    Map(
      "eserviceName" -> eservice,
      "producerName" -> producer,
      "consumerName" -> consumer,
      "today"        -> LocalDate.now().format(printedDateFormatter)
    )

}
