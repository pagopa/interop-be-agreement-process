package it.pagopa.interop.agreementprocess.api.impl
import spray.json._

import scala.io.{BufferedSource, Source}

final case class MailTemplate(subject: String, body: String)

object MailTemplate {

  def activation(): MailTemplate = {
    val source: BufferedSource = Source.fromResource("mailTemplates/activation/activation-mail.json")
    source.getLines().mkString.parseJson.convertTo[MailTemplate]
  }

}
