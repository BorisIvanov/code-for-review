package ru.olekstra.common.rules;

import org.joda.time.DateTime;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class RuleEventStore {

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();

    // Заблокированные IP. value - дата после которой блокировку можно снять и имя события
    private Map<String, BlockItem> blockedIp = new HashMap<>();

    /* Массив данных о срабатывании правил (о событиях).
     * Ключ массива собыий срабатывания правил - название правила (класс) + IP клиента
     * Срабатывания правила (событие), хранятся внутри объекта RuleEventList
     * RuleEventList - хранит события правила, содержи время жизни события
     */
    private Map<RuleEventIpPair, RuleEventList> ruleEventsData = new HashMap<>();

    public void block(String ip, BlockItem blockItem) {
        writeLock.lock();
        try {
            blockedIp.put(ip, blockItem);
        } finally {
            writeLock.unlock();
        }
    }

    public BlockedIpState tryReleaseBlock(String ip) {
        writeLock.lock();
        try {
            BlockItem blockItem = blockedIp.get(ip);
            if (blockItem == null) {
                return BlockedIpState.UNBLOCKED;
            }
            if (blockItem.getExpireTime().isBefore(new DateTime())) {
                ruleEventsData.remove(new RuleEventIpPair(blockItem.getRuleName(), ip));
                blockedIp.remove(ip);
                return BlockedIpState.RELEASED;
            }
            return BlockedIpState.BLOCKED;
        } finally {
            writeLock.unlock();
        }
    }

    public void eventReset(String ruleName, String ip) {
        writeLock.lock();
        try {
            ruleEventsData.remove(new RuleEventIpPair(ruleName, ip));
        } finally {
            writeLock.unlock();
        }
    }

    public RuleEventList getRuleEventsData(String ruleName, String ip) {
        readLock.lock();
        try {
            return ruleEventsData.get(new RuleEventIpPair(ruleName, ip));
        } finally {
            readLock.unlock();
        }
    }

    public int eventAdd(String ip, OlekstraRule rule) {
        DateTime date = new DateTime();
        RuleEventList ruleEventList = null;
        RuleEventIpPair key = new RuleEventIpPair(rule.getName(), ip);

        writeLock.lock();
        try {
            ruleEventList = ruleEventsData.get(key);
            if (ruleEventList == null) {
                ruleEventList = new RuleEventList();
                ruleEventList.setEventLifeMillis(rule.getEventLifeMillis());
                ruleEventsData.put(key, ruleEventList);
            } else {
                eventListClear(ruleEventList, date);
            }

            String reason = rule.getReasonDescription() == null ? "" : rule.getReasonDescription() +
                    (rule.getReason() == null ? "" : ": " + rule.getReason());
            ruleEventList.add(new RuleEvent(date, reason));
        } finally {
            writeLock.unlock();
        }
        return ruleEventList.size();
    }

    /*
     * Одна из главных функций. Проверка для правила, что лог событий устарел, и его можно сбросить.
     * Проверяет работу правила вида - не более 3х неверных запросов каждые 10 минут.
     */
    private void eventListClear(RuleEventList ruleEventList, DateTime date) {
        for (Iterator<RuleEvent> iterator = ruleEventList.iterator(); iterator.hasNext(); ) {
            RuleEvent ruleEvent = iterator.next();
            long millis = date.getMillis() - ruleEvent.getDate().getMillis();
            if (millis > ruleEventList.getEventLifeMillis()) {
                iterator.remove();
            }
        }
    }

    public String eventToString(String ruleName, String ip) {
        RuleEventList ruleEventLog = null;
        readLock.lock();
        try {
            ruleEventLog = this.ruleEventsData.get(new RuleEventIpPair(ruleName, ip));
        } finally {
            readLock.unlock();
        }

        if (ruleEventLog == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (RuleEvent ruleEvent : ruleEventLog) {
            result.append("\n");
            if (ruleEvent.getReason() != null) {
                result.
                        append(ruleEvent.getReason()).
                        append(": ");
            }
            result.append(ruleEvent.getDate().toString());
        }
        return result.toString();
    }

}