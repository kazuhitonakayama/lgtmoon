package controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.io.File
import play.api._
import play.api.mvc._
import org.joda.time.DateTime
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.libs.json._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import akka.actor.Props
import akka.actor.ActorRef
import domain.image.ImageRepository
import actor.ImageActor
import actor.ImageGenerateMessage
import actor.ImageDownloadAndGenerateMessage
import infra.util.UrlBuilder
import infra.datasource.ImageStorage
import javax.inject.{Inject, Named}

/** 画像生成を行うコントロラー */
class ImageGenerateController @Inject() (
  val imageRepository: ImageRepository,
  @Named("image-actor") imageActor: ActorRef

) extends BaseControllerTrait {

  /** postされたurlから画像生成をする */
  def withUrl = Action.async { request =>
    val jsonOpt = request.body.asJson
    jsonOpt match {
      case None => Future(NOT_JSON_RESPONSE)
      case Some(json) => {
          (json \ "url").asOpt[String] match {
            case None => Future(PARAMETER_KEYWORD_NOT_FOUND_RESPONSE)
            case Some(url)  => {
              val xForwardedFor = request.remoteAddress
              Logger.info(xForwardedFor)
              // とりあえずURLだけ先に払い出して返す
              imageRepository.create() map {
                case None => DATABASE_CONNECTION_ERROR_RESPONSE
                case Some(id) => {
                  val lgtmUrl = UrlBuilder.imageUrl(id.toString)
                  imageActor ! ImageDownloadAndGenerateMessage(id, url)
                  Ok(JsonBuilder.imageUrl(lgtmUrl))
                }
              }
            }
          }
      }
    }
  }

  def withBinary = Action.async { request =>
    request.body.asMultipartFormData match {
      case None => Future(INVALID_IMAGE_RESPONSE)
      case Some(data) => {
        data.file("file") match {
          case None => Future(INVALID_IMAGE_RESPONSE)
          case Some(file) => {
            // TODO file validation
            imageRepository.create().map {
              case None => DATABASE_CONNECTION_ERROR_RESPONSE
              case Some(id) => {
                val lgtmImageUrl = UrlBuilder.imageUrl(id.toString)
                val tmpPath = ImageStorage.getTmpPath(id.toString)
                file.ref.moveTo(new File(tmpPath))
                imageActor ! ImageGenerateMessage(id)
                Ok(JsonBuilder.imageUrl(lgtmImageUrl))
              }
            }
          }
        }
      }
    }
  }
}
