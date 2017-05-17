package advice;

import org.aspectj.lang.JoinPoint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация устанавливается на метод. Позволяет только одному потоку сохранять документ.
 * Документ определяется по id {@link Document#getId()}.
 * id документа берется из сигнатуры метода.
 * в name следует указать имя аргумента который является Id довумента
 * или объект который содержит id документа.
 * {@link DocumentNewAdvice#getDocumentId(JoinPoint, OnceThreadByDocument)}}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnceThreadByDocument {
    String name() default "document";
}
