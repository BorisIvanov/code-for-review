package advice;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.systemres.vsrf.lk.dto.document.DocumentBase;
import ru.systemres.vsrf.lk.exception.DocumentLockException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


@Aspect
@Component
public class DocumentNewAdvice {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentNewAdvice.class);
    private static final Map<Long, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    @Value("#{'${document.lock.try.count:4}'}")
    private int configTryCount;
    @Value("#{'${document.lock.try.timeout:300}'}")
    private long configTryTimeout;


    /**
     * Функция возвращает из сигнатуры метода documentId.
     * Имя аргумента содержащего documentId указывается в аннотации
     *
     * @param onceThreadByDocument содержит имя аргумента {@link OnceThreadByDocument#name()}
     */
    private long getDocumentId(JoinPoint joinPoint, OnceThreadByDocument onceThreadByDocument) {
        MethodSignature ms = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = ms.getParameterNames();

        for (int i = 0; i < paramNames.length; i++) {
            if (paramNames[i].equals(onceThreadByDocument.name())) {
                if (joinPoint.getArgs()[i] instanceof Long) {
                    return Long.parseLong(joinPoint.getArgs()[i].toString());
                } else if (joinPoint.getArgs()[i] instanceof DocumentBase) {
                    return ((DocumentBase) joinPoint.getArgs()[i]).getId();
                }
            }
        }

        throw new RuntimeException("DocumentId not found from method signature");
    }

    /**
     * Попытка положить свою блокировку на документ в Map
     */
    private void tryPutLock(ReentrantLock currentLock, long documentId) {
        ReentrantLock ownLock = new ReentrantLock();
        ownLock.lock();
        ReentrantLock newAnotherLock = lockMap.putIfAbsent(documentId, ownLock);
        if (currentLock != null) {
            currentLock.unlock();
        }
        if (newAnotherLock != null) {
            LOGGER.info("AnotherLock exists. document [{}]", documentId);
            ownLock.unlock();
            tryLock(newAnotherLock, 0, documentId);
        }
    }

    /**
     * Ожидание снятия текущей блокировки.
     * Блокировка уже присутствует в Map, необходимо дождаться ее снятия или вылететь по таймауту
     */
    private void tryLock(ReentrantLock currentLock, int tryCount, long documentId) {
        LOGGER.info("Try lock. document [{}] try [{}]", documentId, tryCount);
        if (tryCount >= configTryCount) {
            LOGGER.info("EXCEPTION: Cant lock. Current lock in map. document [{}] try [{}]", documentId, tryCount);
            throw new DocumentLockException(documentId, tryCount);
        }
        try {
            tryCount++;
            if (currentLock == null) {
                tryPutLock(null, documentId);
            } else {
                if (currentLock.tryLock(configTryTimeout, TimeUnit.MILLISECONDS)) {
                    /*
                     * блокировка освобождается после того, как она удалена из Map
                     * 1. создаем свою блокировку и пытаемся добавить в Map
                     * 2.а если возврщает Null - значит текущий поток поставил свою блокировку
                     * 2.б если возвращает блокировку - значит другой поток смог установить блокировку
                     * уходим в ожидание
                     * 3. текущую блокировку можно опустить, она потеряла свой смысл
                     */
                    tryPutLock(currentLock, documentId);
                } else {
                    LOGGER.info("Cant lock. Current lock in map. TryLock is false. document [{}] try [{}]",
                            documentId, tryCount);
                    tryLock(currentLock, tryCount, documentId);
                }
            }
        } catch (InterruptedException e) {
            LOGGER.info("InterruptedException: document [{}] try [{}]", documentId, tryCount);
            throw new DocumentLockException(documentId, tryCount);
        }
    }


    @Around("@annotation(onceThreadByDocument)")
    public Object lock(ProceedingJoinPoint joinPoint, OnceThreadByDocument onceThreadByDocument) throws Throwable {
        LOGGER.info("DocumentNewAdvice lock start");

        long documentId = getDocumentId(joinPoint, onceThreadByDocument);
        if (documentId == 0) {
            LOGGER.info("No need lock new document. document [{}]", documentId);
            return joinPoint.proceed();
        }
        tryPutLock(null, documentId);
        LOGGER.info("Success lock document. document [{}]", documentId);

        try {
            return joinPoint.proceed();
        } finally {
            ReentrantLock lock = lockMap.remove(documentId);
            lock.unlock();
            LOGGER.info("Success unlock. document [{}]", documentId);
        }
    }


}