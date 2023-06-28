package it.pagopa.interop.agreementprocess.common.readmodel

trait ReadModelQuery {
  def mapToVarArgs[A, B](l: Seq[A])(f: Seq[A] => B): Option[B] = Option.when(l.nonEmpty)(f(l))
}
