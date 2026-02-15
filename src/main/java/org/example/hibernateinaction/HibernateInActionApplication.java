package org.example.hibernateinaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.*;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import net.datafaker.providers.base.Gender;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Formula;
import org.hibernate.boot.MetadataSources;
import org.hibernate.envers.*;
import org.hibernate.envers.strategy.internal.DefaultAuditStrategy;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
@EnableRetry
@EnableCaching
public class HibernateInActionApplication {

    public static void main(String[] args) {
        SpringApplication.run(HibernateInActionApplication.class, args);
    }

    /*
    CommandLineRunner, Spring Boot uygulaman ayağa kalktıktan hemen sonra otomatik olarak
    bir kod parçasını çalıştırmak istiyorsan kullanılan bir "başlangıç tetikleyicisi"dir.
    Veritabanını Beslemek (Seed Data): Uygulama her çalıştığında DB'de hazır kullanıcılar veya ürünler olsun istiyorsan.
    Test/Debug: Uygulama başlar başlamaz bir servisin çalışıp çalışmadığını konsoldan görmek için.
    Loglama: "Uygulama başarıyla ayağa kalktı" gibi özel kontroller yapmak için.

     */
    @Bean
    CommandLineRunner commandLineRunner(final CustomerRepository customerRepository, final LocationService locationService, CustomerService customerService) {

        return args -> {

//            Customer customer = new Customer();
//            customer.setName("Ada");
//            customer.setCustomerType(CustomerType.SUPER_CUSTOMER);
//            customer.setBalance(BigDecimal.ZERO);
//
//            Metadata metadata = new Metadata();
//            metadata.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
//            metadata.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
//            customer.setMetadata(metadata);
//
//            customerRepository.save(customer);

//            Product product1 = new Product();
//            product1.setTitle("phone");
//            product1.setCustomer(customer);
//            product1.setPrice(BigDecimal.valueOf(100));
//
//            Product product2 = new Product();
//            product2.setTitle("pc");
//            product2.setCustomer(customer);
//            product2.setPrice(BigDecimal.valueOf(200));
//
//            productRepository.saveAll(Arrays.asList(product1, product2));


//            System.out.println(customer);


//            Faker faker = new Faker();
//            for (int i = 0; i < 100; i++) {
//                Customer customer = new Customer();
//                customer.setName(faker.name().firstName());
//                customer.setSurname(faker.name().lastName());
//                customer.setEmail(faker.internet().emailAddress());
//                customer.setBalance(new BigDecimal(faker.number().numberBetween(1, 100)));
//                customer.setAge(faker.number().numberBetween(1, 100));
//                customer.setCustomerType(CustomerType.NORMAL_CUSTOMER);
//                customer.setPhone(faker.phoneNumber().phoneNumber());
//                customer.setGender(faker.gender().types());
//
//                customerRepository.save(customer);
//            }

            customerService.insertBatch();
            List<CountByGender> countByGender = customerRepository.groupByGender();
            System.out.println(countByGender);

        };
    }

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "HibernateInActionApplication");
    }
    //grafanada okunurlugu arttirmak icin

}

interface CustomerRepository extends JpaRepository<Customer, Long> {

    /*
    duz sorguda 500 aliriz cunku transaction alinmasi gerekir
    ama sonrasinda versiyonda koyman gerekir!!
    OPTIMISTIC_FORCE_INCREMENT kendisi +1 daha yapar
    ab -n 100 -c 2  http://localhost:8080/customers/inc

    ama entity de version zaten +1 yapar o yuzden sadece optimistic yapmak yeterli olacaktir
    ama bu ornekte cozum olmayacaktir ama version ve balance dogru sekilde gider

    ab -n 100 -c 2  http://localhost:8080/customers/inc
    pessimisticde  ise bir yerlerde eksik deger ekler ve deadlock olusur

    SHOW  ENGINE INNODB STATUS  mysqlde bir motordur lowlevel loglari gosterir deadlock loglarini gorebilirsin
    spring CannotAcquireLockException verir
     */

    //@Lock(LockModeType.OPTIMISTIC)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Customer findByName(String name);

    @Query("SELECT new org.example.hibernateinaction.CountByGender(c.gender as gender, count(c) as count)  FROM Customer c group by c.gender")
    List<CountByGender> groupByGender();


}

@AllArgsConstructor
@NoArgsConstructor
@Data
class CountByGender{
    private String gender;
    private long count;
}

interface LocationRepository extends JpaRepository<Location, Long> {


}

//interface ProductRepository extends JpaRepository<Product, Long> {}

@Data
@Builder
@Entity
@Cacheable
@Cache(region = "customer",usage = CacheConcurrencyStrategy.READ_WRITE)
@NoArgsConstructor
@AllArgsConstructor
@Audited
//caching esnasinda kitleme yapar bu stratji veri tutarliligini saglamak icin transaction bitmesini bekler
class Customer implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String name;

    private BigDecimal balance;

    private String surname;

    private String phone;

    private String email;

    private Integer age;

    private String gender;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING) // artik string degeri neyse onu yazar
    private CustomerType customerType; // db de 0,1,2 olarak tutulur

    @JsonIgnore
    private Metadata metadata;

//    @OneToMany(cascade = CascadeType.ALL,mappedBy = "customer", fetch = FetchType.LAZY) // zincirleme veri varsa onlarida hallet demek
//    //mappedby diger tabloda beni nasil aniyorlar diye
//    private List<Product> products;
}

enum CustomerType{
    ELITE_CUSTOMER,
    SUPER_CUSTOMER,
    NORMAL_CUSTOMER
    //biri bunlarin yerini degistirirse db ye veri tutarsizligi olur o yuzden yukarida @Enumerated kullan
}

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@RevisionEntity(AuditRevisionListener.class)
@Table(name = "revinfo")
class AuditRevisionEntity extends DefaultRevisionEntity {
    private String user;
}

class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {

        String currentUser = Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .map(User.class::cast)
                .map(User::getUsername)
                .orElse("unknown user");

        AuditRevisionEntity audit = (AuditRevisionEntity) revisionEntity;
        audit.setUser(currentUser);
    }



}

@Data
@Entity
class Location{

    @Id
    @GeneratedValue
    private Long id;

    private Point point;

}


//@Data
//@Entity
//class Product {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    private String title;
//
//    private BigDecimal price;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    private Customer customer;
//
//    @Formula("price * 0.18")
//    private BigDecimal tax; //veritabanina yazmaz transient olarak algilar hibernate formula sayesinde
//
//
//    private Metadata metadata;
//
//
//}

@Embeddable //uzun uzun kullanimlari engeller daha temiz kod olur
@Data
class Metadata{

    private Timestamp createdAt;

    private Timestamp updatedAt;
}

@Service
@RequiredArgsConstructor
@Slf4j
class CustomerService {
    private final CustomerRepository customerRepository;

    @Transactional
    public List<Customer> findAll() throws InterruptedException {
        Thread.sleep(5000);
        return customerRepository.findAll();
    }

    //@Transactional(isolation = Isolation.SERIALIZABLE)
    //en kati olandir datayi saglam locklar!!!
//    @Retryable(
//            retryFor = { ObjectOptimisticLockingFailureException.class },
//            maxAttempts = 3,
//            backoff = @Backoff(delay = 5000,multiplier = 2)
//    )
    /*
    retryFor: Sadece bu hata türü oluştuğunda tekrar dene. (Optimistic Lock hatası).
    maxAttempts: Toplam deneme sayısı. (Burada 3 kez şansını deneyecek).
    backoff: Denemeler arasındaki bekleme süresi.
    delay = 500: İlk hatadan sonra 5000ms bekle.
    multiplier = 2: (Opsiyonel) Her seferinde bekleme süresini iki katına çıkar (500ms, 1000ms, 2000ms gibi).
     Buna "Exponential Backoff" denir ve sistemi rahatlatır.
         */
    @Transactional
    public void increment() {
        Customer c = this.customerRepository.findById(28L).orElse(null);
        c.setBalance(c.getBalance().add(BigDecimal.ONE));
        customerRepository.save(c);
    }

    //@Recover: Eğer tüm denemeler başarısız olursa (maxAttempts dolarsa) devreye giren "kurtarma" metodudur.
    // Parametreleri orijinal metotla aynı olmalıdır.
    @Recover
    public void recover(ObjectOptimisticLockingFailureException e) {
        log.error(e.getMessage());
    }

    public Customer findById(Long id) {
        return this.customerRepository.findById(id).orElse(null);
    }

    @Transactional
    public void insertBatch(){

        Faker faker = new Faker();
        List<Customer> customers = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Customer customer = new Customer();
            customer.setName(faker.name().firstName());
            customer.setSurname(faker.name().lastName());
            customer.setEmail(faker.internet().emailAddress());
            customer.setBalance(new BigDecimal(faker.number().numberBetween(1, 100)));
            customer.setAge(faker.number().numberBetween(1, 100));
            customer.setCustomerType(CustomerType.NORMAL_CUSTOMER);
            customer.setPhone(faker.phoneNumber().phoneNumber());
            customer.setGender(faker.gender().types());
            customers.add(customer);


        }
        this.customerRepository.saveAll(customers);

    }
}

@Service
@RequiredArgsConstructor
class LocationService {
    private final LocationRepository locationRepository;

    void save() throws ParseException {
        Location location = new Location();
        Geometry g = wktToGeometry("POINT (2 5)");
        location.setPoint(g.getInteriorPoint());
        this.locationRepository.save(location);
    }

    public Geometry wktToGeometry(String wellKnownText) throws ParseException {
        return new WKTReader().read(wellKnownText);
    }
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/customers")
class CustomerController {
    private final CustomerService customerService;

    @GetMapping("/all")
    public List<Customer> findAll() throws InterruptedException {
        return this.customerService.findAll();
    }

    @GetMapping("/inc")
    void increment(){
        this.customerService.increment();
        // ab -n 100 -c 2  http://localhost:8080/customers/inc
        //bu istek gelirse +100 olacagina +50 olacaktir ve veri bozulacaktir
    }

    @GetMapping("/{id}")
    public Customer findById(@PathVariable Long id) {
        return this.customerService.findById(id);
    }


}