package it.pagopa.interop.agreementprocess.server.impl

import com.typesafe.scalalogging.Logger

import scala.concurrent.Future

class HealthCheck() extends (() => Future[Boolean]) {

  private val log = Logger(this.getClass)

  override def apply(): Future[Boolean] = {
    log.trace("HealthCheck called")
    Future.successful(true)
  }
}

class LiveCheck() extends (() => Future[Boolean]) {

  private val log = Logger(this.getClass)

  override def apply(): Future[Boolean] = {
    log.trace("LiveCheck called")
    Future.successful(true)
  }
}
