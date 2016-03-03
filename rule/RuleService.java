package ru.olekstra.common.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.dozer.Mapper;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ru.olekstra.awsutils.AWSAccess;
import ru.olekstra.awsutils.DynamodbService;
import ru.olekstra.awsutils.SnsService;
import ru.olekstra.awsutils.exception.ItemSizeLimitExceededException;
import ru.olekstra.common.exception.NotRegisteredRuleException;
import ru.olekstra.common.helper.DozerHelper;
import ru.olekstra.common.rules.BlockedIpState;
import ru.olekstra.common.rules.RuleEventStore;
import ru.olekstra.common.rules.BlockItem;
import ru.olekstra.common.rules.OlekstraRule;
import ru.olekstra.common.rules.RuleEventList;
import ru.olekstra.domain.AccessRulesEvents;

@Service
public class RuleService {
    private final DateTimeFormatter periodFormatter = DateTimeFormat.forPattern("yyyyMM");

    private RuleEventStore ruleEventStore = new RuleEventStore();


    @Autowired
    private SnsService snsService;
    @Autowired
    private DynamodbService dynamodbService;
    @Autowired
    private Mapper dozerBeanMapper;

    public RuleEventList getRuleEventsData(String ruleName, String ip) {
        return ruleEventStore.getRuleEventsData(ruleName, ip);
    }

    public void resetEvent(String eventName, String ip) {
        ruleEventStore.eventReset(eventName, ip);
    }

    public void onEvent(String ip, OlekstraRule rule)
            throws InterruptedException, ItemSizeLimitExceededException, NotRegisteredRuleException {

        int eventSize = ruleEventStore.eventAdd(ip, rule);

        long sleep = 0;
        if (rule.getLimitToSleep() != null && eventSize > rule.getLimitToSleep()) {
            writeLog(AccessRulesEvents.SLEEP_TYPE, ip, rule.getName(), null, null);
            sleep = rule.getSleepMillis();
        }

        if (rule.getLimitToBlock() != null && eventSize > rule.getLimitToBlock()) {
            blockIp(ip, rule.getName(), rule.getBlockMillis());
        }

        if (sleep > 0 && AWSAccess.IPBLOCK) {
            currentTreadSleep(sleep);
        }
    }

    private void currentTreadSleep(long sleep) throws InterruptedException {
        synchronized (Thread.currentThread()) {
            Thread.currentThread().wait(sleep);
        }
    }

    public void blockIp(String ip, String ruleName, long millisToExpire) throws ItemSizeLimitExceededException {
        if (isBlocked(ip)) {
            return;
        }

        DateTime timeToExpire = (new DateTime()).plusMillis((int) millisToExpire);

        BlockItem blockItem = new BlockItem();
        blockItem.setExpireTime(timeToExpire);
        blockItem.setRuleName(ruleName);
        ruleEventStore.block(ip, blockItem);
        String eventLog = ruleEventStore.eventToString(ruleName, ip);

        int minitesToExpire = (int) (millisToExpire / 1000) / 60;
        writeLog(AccessRulesEvents.BLOCK_TYPE, ip, ruleName, minitesToExpire, eventLog);
    }

    private void writeLog(String type, String ip, String event, Integer mins, String log) throws ItemSizeLimitExceededException {
        AccessRulesEvents blockLog = new AccessRulesEvents();
        blockLog.setPeriod(AccessRulesEvents.getHashkey(new DateTime()));
        blockLog.setIp(ip);
        blockLog.setEvent(event);
        blockLog.setRangeKey(new DateTime());
        blockLog.setExpireAfterMin(mins);
        blockLog.setLog(log);
        blockLog.setType(type);
        dynamodbService.putObjectOrDie(blockLog);
    }

    public boolean isBlocked(String ip) throws ItemSizeLimitExceededException {
        BlockedIpState state = ruleEventStore.tryReleaseBlock(ip);
        if (state == BlockedIpState.RELEASED) {
            writeLog(AccessRulesEvents.UNBLOCK_TYPE, ip, null, null, null);
            return false;
        } else if (state == BlockedIpState.UNBLOCKED) {
            return false;
        }
        return true;
    }

    public List<ru.olekstra.domain.dto.AccessRuleEvent> getBlockIpLog(String period)
            throws IllegalAccessException, InstantiationException, IOException {
        String key = period;
        if (key == null) {
            key = periodFormatter.print(new DateTime());
        }
        List<ru.olekstra.domain.AccessRulesEvents> list = dynamodbService
                .queryObjects(ru.olekstra.domain.AccessRulesEvents.class, key);
        return DozerHelper.map(dozerBeanMapper, list, ru.olekstra.domain.dto.AccessRuleEvent.class);
    }
}