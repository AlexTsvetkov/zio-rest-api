package scala.school.infrastructure.slick

import scala.school.domain.ItemId
import slick.jdbc.JdbcProfile

trait Profile {
  val profile: JdbcProfile
}

trait EntityIdMappers {
  self: Profile =>
  import profile.api._

  implicit def itemIdMapper: BaseColumnType[ItemId] = MappedColumnType.base[ItemId, Long](
    ent => ent.value,
    value => ItemId(value)
  )
}
