package com.dnu.sfm;


import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * {@code @EnableCaching} activates Spring's cache abstraction so that
 * {@code @Cacheable} / {@code @CacheEvict} on the deposit settlement
 * calculations actually take effect (a no-op annotation without it).
 */
@EnableCaching
@SpringBootApplication
// Указываем корректный класс диалекта с .community.
// Добавляем внутренние персистеры Hibernate в регистрацию рефлексии
@RegisterReflectionForBinding({
        org.sqlite.JDBC.class,
        org.hibernate.community.dialect.SQLiteDialect.class,
        // Классы, отвечающие за стратегию JOINED и SINGLE_TABLE наследования сущностей:
        org.hibernate.persister.entity.JoinedSubclassEntityPersister.class,
        org.hibernate.persister.entity.SingleTableEntityPersister.class,
        org.hibernate.persister.entity.UnionSubclassEntityPersister.class,
        // Добавляем утилиту списков Thymeleaf для сохранения её методов:
        org.thymeleaf.expression.Lists.class
})
public class SfmApplication {

        public static void main(String[] args) {
                SpringApplication.run(SfmApplication.class, args);
        }

}
