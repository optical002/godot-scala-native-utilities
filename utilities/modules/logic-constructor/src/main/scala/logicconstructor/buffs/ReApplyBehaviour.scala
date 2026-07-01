package logicconstructor.buffs

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

enum ReApplyBehaviour:
    case RefreshDuration
    case Stacks(max: Int, onExpire: StackExpiry)

enum StackExpiry:
    case ReduceStack, RemoveBuff

object StackExpiry:

    given ConfigReader[StackExpiry] = ConfigReader.fromCursor: cur =>
        cur.asString.flatMap:
            case "ReduceStack" => Right(StackExpiry.ReduceStack)
            case "RemoveBuff"  => Right(StackExpiry.RemoveBuff)
            case other =>
                cur.failed(CannotConvert(
                    other,
                    "StackExpiry",
                    s"unknown on-expire '$other', expected ReduceStack or RemoveBuff"
                ))

object ReApplyBehaviour:

    given ConfigReader[ReApplyBehaviour] = ConfigReader.fromCursor: cur =>
        cur.asString match
            case Right("RefreshDuration") => Right(ReApplyBehaviour.RefreshDuration)
            case Right("Stacks") =>
                cur.failed(CannotConvert(
                    "Stacks",
                    "ReApplyBehaviour",
                    "re-apply 'Stacks' requires a body, e.g. { Stacks: { max: 3, on-expire: ReduceStack } }"
                ))
            case Right(other) =>
                cur.failed(CannotConvert(
                    other,
                    "ReApplyBehaviour",
                    s"unknown re-apply behaviour '$other', expected RefreshDuration or Stacks"
                ))
            case Left(_) =>
                cur.asObjectCursor.flatMap: obj =>
                    if obj.keys.size != 1 then
                        cur.failed(CannotConvert(
                            obj.keys.mkString("{", ", ", "}"),
                            "ReApplyBehaviour",
                            "re-apply object must have exactly one variant key"
                        ))
                    else
                        obj.keys.head match
                            case "Stacks" =>
                                obj.atKey("Stacks").flatMap(_.asObjectCursor).flatMap: inner =>
                                    for
                                        maxCur <- inner.atKey("max")
                                        max <- maxCur.asInt
                                        _ <- if max == 0 then
                                            cur.failed(CannotConvert(
                                                "0",
                                                "ReApplyBehaviour",
                                                "Stacks 'max' must be at least 1"
                                            ))
                                        else Right(())
                                        expiryCur <- inner.atKey("on-expire")
                                        onExpire <- summon[ConfigReader[StackExpiry]].from(expiryCur)
                                    yield ReApplyBehaviour.Stacks(max, onExpire)
                            case other =>
                                cur.failed(CannotConvert(
                                    other,
                                    "ReApplyBehaviour",
                                    s"unknown re-apply behaviour '$other', expected RefreshDuration or Stacks"
                                ))
