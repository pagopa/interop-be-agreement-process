package it.pagopa.interop.agreementprocess.service

import com.openhtmltopdf.util.XRLog
import it.pagopa.interop.agreementprocess.service.util.PDFPayload
import it.pagopa.interop.commons.files.model.PDFConfiguration
import it.pagopa.interop.commons.files.service.PDFManager
import it.pagopa.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.interop.tenantmanagement.model.tenant.{
  PersistentCertifiedAttribute,
  PersistentDeclaredAttribute,
  PersistentVerifiedAttribute
}

import java.io.ByteArrayOutputStream
import java.time.{OffsetDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import scala.concurrent.Future
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.util.Try

trait PDFCreator {
  def create(template: String, pdfPayload: PDFPayload): Future[Array[Byte]]

}

object PDFCreator extends PDFCreator with PDFManager {
  private final val RomeZoneId: ZoneId = ZoneId.of("Europe/Rome")

  // Suppressing openhtmltopdf log
  XRLog.listRegisteredLoggers.asScala.foreach((logger: String) =>
    XRLog.setLevel(logger, java.util.logging.Level.SEVERE)
  )
  private[this] val pdfConfigs: PDFConfiguration = PDFConfiguration(resourcesBaseUrl = Some("/agreementTemplate/"))
  private[this] val printedDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  private[this] val printedTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

  override def create(template: String, pdfPayload: PDFPayload): Future[Array[Byte]] = {
    def toByteArray: ByteArrayOutputStream => Try[Array[Byte]] =
      getPDF[ByteArrayOutputStream, Array[Byte]](template, setupData(pdfPayload), pdfConfigs)(_.toByteArray)

    toByteArray(new ByteArrayOutputStream()).toFuture
  }

  private def setupData(pdfPayload: PDFPayload): Map[String, String] = {
    val todayDate = getDateText(pdfPayload.today)
    val todayTime = getTimeText(pdfPayload.today)

    val submissionDate = getDateText(pdfPayload.submissionTimestamp)
    val submissionTime = getTimeText(pdfPayload.submissionTimestamp)

    val activationDate = getDateText(pdfPayload.activationTimestamp)
    val activationTime = getTimeText(pdfPayload.activationTimestamp)

    Map(
      "todayDate"           -> todayDate,
      "todayTime"           -> todayTime,
      "agreementId"         -> pdfPayload.agreementId.toString(),
      "submitter"           -> pdfPayload.submitter,
      "declaredAttributes"  -> getDeclaredAttributesText(pdfPayload.declared),
      "verifiedAttributes"  -> getVerifiedAttributesText(pdfPayload.verified),
      "certifiedAttributes" -> getCertifiedAttributesText(pdfPayload.certified),
      "submissionDate"      -> submissionDate,
      "submissionTime"      -> submissionTime,
      "activationDate"      -> activationDate,
      "activationTime"      -> activationTime,
      "activator"           -> pdfPayload.activator,
      "eServiceName"        -> pdfPayload.eService,
      "producerText" -> getTenantText(pdfPayload.producerName, pdfPayload.producerOrigin, pdfPayload.producerIPACode),
      "consumerText" -> getTenantText(pdfPayload.consumerName, pdfPayload.consumerOrigin, pdfPayload.consumerIPACode)
    )
  }

  private def getTenantText(name: String, origin: String, value: String): String = {
    if (origin == "IPA")
      s"$name (codice IPA: ${value})"
    else name
  }

  private def getDeclaredAttributesText(declared: Seq[(ClientAttribute, PersistentDeclaredAttribute)]): String =
    declared.map { case (clientAttribute, tenantAttribute) =>
      val date = getDateText(tenantAttribute.assignmentTimestamp)
      val time = getTimeText(tenantAttribute.assignmentTimestamp)
      s"""
         |<div>
         |In data <strong>$date</strong> alle ore <strong>$time</strong>,
         |l’Infrastruttura ha registrato la dichiarazione del Fruitore di possedere il seguente attributo <strong>${clientAttribute.name}</strong> dichiarato
         |ed avente il seguente periodo di validità ________,
         |necessario a soddisfare il requisito di fruizione stabilito dall’Erogatore per l’accesso all’E-service.
         |</div>
         |""".stripMargin
    }.mkString

  private def getCertifiedAttributesText(certified: Seq[(ClientAttribute, PersistentCertifiedAttribute)]): String =
    certified.map { case (clientAttribute, tenantAttribute) =>
      val date = getDateText(tenantAttribute.assignmentTimestamp)
      val time = getTimeText(tenantAttribute.assignmentTimestamp)
      s"""
         |<div>
         |In data <strong>$date</strong> alle ore <strong>$time</strong>,
         |l’Infrastruttura ha registrato il possesso da parte del Fruitore del seguente attributo <strong>${clientAttribute.name}</strong> certificato,
         |necessario a soddisfare il requisito di fruizione stabilito dall’Erogatore per l’accesso all’E-service.
         |</div>
         |""".stripMargin
    }.mkString

  private def getVerifiedAttributesText(verified: Seq[(ClientAttribute, PersistentVerifiedAttribute)]): String =
    verified.map { case (clientAttribute, tenantAttribute) =>
      val date = getDateText(tenantAttribute.assignmentTimestamp)
      val time = getTimeText(tenantAttribute.assignmentTimestamp)
      // TODO add implicit verifier when ready
      s"""
         |<div>
         |In data <strong>$date</strong> alle ore <strong>$time</strong>,
         |l’Infrastruttura ha registrato la dichiarazione del Fruitore di possedere il seguente attributo <strong>${clientAttribute.name}</strong>,
         |verificata dall’aderente ________ OPPURE dall’Erogatore stesso in data <strong>$date</strong>,
         |necessario a soddisfare il requisito di fruizione stabilito dall’Erogatore per l’accesso all’E-service.
         |</div>
         |""".stripMargin
    }.mkString

  private def getDateText(timestamp: OffsetDateTime): String =
    timestamp.atZoneSameInstant(RomeZoneId).toLocalDate.format(printedDateFormatter)

  private def getTimeText(timestamp: OffsetDateTime): String =
    timestamp.atZoneSameInstant(RomeZoneId).toLocalTime.format(printedTimeFormatter)

}
