package t2x.smqd.session

/**
  * 2018. 6. 11. - Created by Kwon, Yeong Eon
  */
object SessionId {
  def apply(id: String) = new SessionId(id)

  def fromActorName(actorName: String): String = {
    if (actorName.startsWith("_")) {
      actorName.substring(1)
    }
    else {
      actorName
    }
  }

  // The actor name must not be empty or start with $, but it may contain URL encoded characters
  def toActorName(id: String): String = "_" + id
}

import t2x.smqd.session.SessionId._

class SessionId(val id: String) {
  val actorName: String = toActorName(id)

  override def toString: String = id

  override def hashCode(): Int = id.hashCode
}
