package it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl

import it.pagopa.pdnd.interop.uservice.agreementprocess.model.Agreement

import scala.concurrent.Future

object AgreementFilter {

  /** Returns the sequence of filtered agreements by latest version if the <code>filterLatest</code> parameter is set to true.
    * @param filterLatest
    * @param agreements
    * @return
    */
  def filterAgreementsByLatestVersion(
    filterLatest: Option[Boolean],
    agreements: Seq[Agreement]
  ): Future[Seq[Agreement]] = {

    filterLatest match {
      case Some(true) =>
        val currentAgreements =
          agreements
            .groupBy(_.eservice.id)
            .map { case (eserviceId, eserviceAgreements) =>
              (
                eserviceId,
                eserviceAgreements.sortWith((a, b) => {
                  Ordering[Option[Long]].gt(a.eservice.version.toLongOption, b.eservice.version.toLongOption)
                })
              )
            }
            .values
            .map(v => v.headOption)
            .flatten
            .toSeq

        Future.successful[Seq[Agreement]](currentAgreements)
      case _ => Future.successful(agreements)
    }
  }
}
