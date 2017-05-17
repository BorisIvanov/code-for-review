package service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;



public class DocumentNewService  {

    @OnceThreadByDocument(name = "id")
    public long copy(long id, User user) {
        return 0;
    }

    @OnceThreadByDocument
    public void save(DocumentBase document) {

    }

    @OnceThreadByDocument(name = "documentRequest")
    public void save(DocumentTemplate documentTemplate, DocumentRequest documentRequest, boolean prepareHTML, Locale
            locale) {
    }

    @OnceThreadByDocument(name = "documentId")
    public Document remove(long documentId, long userId) {
        return null;
    }

}