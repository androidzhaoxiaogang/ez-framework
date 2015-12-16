package com.asto.ez.framework.rpc.http

import java.io.File

import com.asto.ez.framework.interceptor.InterceptorProcessor
import com.asto.ez.framework.rpc.{EChannel, Fun, Router}
import com.asto.ez.framework.{EZContext, EZGlobal}
import com.ecfront.common.{JsonHelper, Resp}
import com.typesafe.scalalogging.slf4j.LazyLogging
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.{HttpServerFileUpload, HttpServerRequest, HttpServerResponse}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HttpServerProcessor extends Handler[HttpServerRequest] with LazyLogging {

  override def handle(request: HttpServerRequest): Unit = {
    if (request.method().name() == "OPTIONS") {
      returnContent("", request.response(), "text/html")
    } else if (request.path() != "/favicon.ico") {
      val ip =
        if (request.headers().contains("X-Forwarded-For") && request.getHeader("X-Forwarded-For").nonEmpty) {
          request.getHeader("X-Forwarded-For")
        } else {
          request.remoteAddress().host()
        }
      logger.trace(s"Receive a request [${request.uri()}] , from $ip ")
      try {
        router(request, ip)
      } catch {
        case ex: Throwable =>
          logger.error("Http process error.", ex)
          returnContent(s"请求处理错误：${ex.getMessage}", request.response(), "text/html")
      }
    }
  }

  private def router(request: HttpServerRequest, ip: String): Unit = {
    val accept =
      if (request.headers().contains("Accept") && request.headers().get("Accept") != "*.*") request.headers().get("Accept").split(",")(0).toLowerCase else "application/json"
    val contentType =
      if (request.headers().contains("Content-Type")) request.headers().get("Content-Type").toLowerCase else "application/json; charset=UTF-8"
    val result = Router.getFunction(EChannel.HTTP, request.method().name(), request.path(), request.params().map(entry => entry.getKey -> entry.getValue).toMap)
    val parameters = result._3
    if (result._1) {
      val context = EZContext()
      context.remoteIP = ip
      context.method = request.method().name()
      context.templateUri = result._4
      context.realUri = request.path()
      context.parameters = parameters
      context.accept = accept
      context.contentType = contentType
      execute(request, result._2, context)
    } else {
      returnContent(result._1, request.response())
    }
  }

  private def execute(request: HttpServerRequest, fun: Fun[_], context: EZContext):Unit = {
    if (request.headers().contains("Content-Type") && request.headers.get("Content-Type").toLowerCase.startsWith("multipart/form-data")) {
      //上传处理
      request.setExpectMultipart(true)
      request.uploadHandler(new Handler[HttpServerFileUpload] {
        override def handle(upload: HttpServerFileUpload): Unit = {
          var path = if (request.params().contains("path")) request.params().get("path") else ""
          if (path.nonEmpty && !path.endsWith(File.separator)) {
            path += File.separator
          }
          val newName = if (request.params().contains("name")) request.params().get("name")
          else {
            if (upload.filename().contains(".")) {
              upload.filename().substring(0, upload.filename().lastIndexOf(".")) + "_" + System.nanoTime() + "." + upload.filename().substring(upload.filename().lastIndexOf(".") + 1)
            } else {
              upload.filename() + "_" + System.nanoTime()
            }
          }
          val tPath = EZGlobal.ez_rpc_http_resource_path + path + newName
          upload.exceptionHandler(new Handler[Throwable] {
            override def handle(e: Throwable): Unit = {
              returnContent(Resp.serverError(e.getMessage), request.response(), context.accept)
            }
          })
          upload.endHandler(new Handler[Void] {
            override def handle(e: Void): Unit = {
              context.contentType = "application/json; charset=UTF-8"
              execute(request, path + newName, fun, context, request.response())
            }
          })
          upload.streamToFileSystem(tPath)
        }
      })
    } else if (request.method().name() == "POST" || request.method().name() == "PUT") {
      //Post或Put请求，需要处理Body
      request.bodyHandler(new Handler[Buffer] {
        override def handle(data: Buffer): Unit = {
          execute(request, data.getString(0, data.length), fun, context, request.response())
        }
      })
    } else {
      //Get或Delete请求
      execute(request, null, fun, context, request.response())
    }
  }

  private def execute(request: HttpServerRequest, body: Any, fun: Fun[_], context: EZContext, response: HttpServerResponse):Unit= {
    InterceptorProcessor.process[EZContext](HttpInterceptor.category, context, {
      (info, interContext) =>
        try {
          val b = if (body != null) {
            context.contentType match {
              case t if t.contains("json") => JsonHelper.toObject(body, fun.requestClass)
              case _ => logger.error("Not support content type:" + context.contentType)
            }
          } else {
            null
          }
          fun.execute(context.parameters, b, context).onSuccess {
            case excResp =>
              returnContent(excResp, response, context.accept)
          }
          Future(Resp.success(null))
        } catch {
          case e: Exception =>
            logger.error("Execute function error.", e)
            returnContent(Resp.serverError(e.getMessage), response, context.accept)
            Future(Resp.success(null))
        }
    }, {
      (resp, context) =>
        returnContent(resp, request.response())
        Future(Resp.success(null))
    })
  }

  private def returnContent(result: Any, response: HttpServerResponse, accept: String = "application/json; charset=UTF-8") {
    //支持CORS
    val res = result match {
      case r: String => r
      case _ => JsonHelper.toJsonString(result)
    }
    logger.trace("Response: \r\n" + res)
    response.setStatusCode(200).putHeader("Content-Type", accept)
      .putHeader("Cache-Control", "no-cache")
      .putHeader("Access-Control-Allow-Origin", "*")
      .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
      .putHeader("Access-Control-Allow-Headers", "Content-Type, X-Requested-With, X-authentication, X-client")
      .end(res)
  }

}
