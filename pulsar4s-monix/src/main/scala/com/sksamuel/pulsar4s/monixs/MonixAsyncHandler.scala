package com.sksamuel.pulsar4s.monixs

import java.util.concurrent.CompletableFuture

import com.sksamuel.pulsar4s.{AsyncHandler, Message, MessageId}
import monix.eval.Task
import org.apache.pulsar.client.api
import org.apache.pulsar.client.api.Reader

import scala.compat.java8.FutureConverters
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

class MonixAsyncHandler extends AsyncHandler[Task] {

  implicit def completableTToFuture[T](f: CompletableFuture[T]): Future[T] =
    FutureConverters.toScala(f)

  implicit def completableVoidToTask(f: CompletableFuture[Void]): Task[Unit] =
    Task.deferFuture(FutureConverters.toScala(f)).map(_ => ())

  override def failed(e: Throwable): Task[Nothing] = Task.raiseError(e)

  override def send[T](t: T, producer: api.Producer[T]): Task[MessageId] = {
    Task.deferFuture {
      val future = producer.sendAsync(t)
      FutureConverters.toScala(future)
    }.map { id => MessageId.fromJava(id) }
  }

  override def receive[T](consumer: api.Consumer[T]): Task[Message[T]] = {
    Task.deferFuture {
      val future = consumer.receiveAsync()
      FutureConverters.toScala(future)
    }.map { msg => Message.fromJava(msg) }
  }

  def unsubscribeAsync(consumer: api.Consumer[_]): Task[Unit] = consumer.unsubscribeAsync()

  override def close(producer: api.Producer[_]): Task[Unit] = producer.closeAsync()
  override def close(consumer: api.Consumer[_]): Task[Unit] = consumer.closeAsync()

  override def seekAsync(consumer: api.Consumer[_], messageId: MessageId): Task[Unit] = consumer.seekAsync(messageId)

  override def transform[A, B](t: Task[A])(fn: A => Try[B]): Task[B] =
    t.flatMap { a =>
      fn(a) match {
        case Success(b) => Task.now(b)
        case Failure(e) => Task.raiseError(e)
      }
    }

  override def acknowledgeAsync[T](consumer: api.Consumer[T], messageId: MessageId): Task[Unit] =
    consumer.acknowledgeAsync(messageId)

  override def acknowledgeCumulativeAsync[T](consumer: api.Consumer[T], messageId: MessageId): Task[Unit] =
    consumer.acknowledgeCumulativeAsync(messageId)

  override def close(reader: Reader[_]): Task[Unit] = reader.closeAsync()

  override def nextAsync[T](reader: Reader[T]): Task[Message[T]] = Task.deferFuture(reader.readNextAsync()).map(Message.fromJava)
}

object MonixAsyncHandler {
  implicit def handler: AsyncHandler[Task] = new MonixAsyncHandler
}
