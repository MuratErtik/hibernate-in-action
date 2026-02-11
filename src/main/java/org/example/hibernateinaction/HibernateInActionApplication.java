package org.example.hibernateinaction;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import lombok.*;
import org.hibernate.annotations.Formula;
import org.hibernate.boot.MetadataSources;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
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
    CommandLineRunner commandLineRunner(final CustomerRepository customerRepository, final ProductRepository productRepository) {

        return args -> {

            Customer customer = new Customer();
            customer.setName("John Doe");
            customer.setCustomerType(CustomerType.SUPER_CUSTOMER);

            Metadata metadata = new Metadata();
            metadata.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
            metadata.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
            customer.setMetadata(metadata);

            customerRepository.save(customer);

            Product product1 = new Product();
            product1.setTitle("phone");
            product1.setCustomer(customer);
            product1.setPrice(BigDecimal.valueOf(100));

            Product product2 = new Product();
            product2.setTitle("pc");
            product2.setCustomer(customer);
            product2.setPrice(BigDecimal.valueOf(200));

            productRepository.saveAll(Arrays.asList(product1, product2));


            System.out.println(customer);




        };
    }

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "HibernateInActionApplication");
    }

}

interface CustomerRepository extends JpaRepository<Customer, Long> {
}

interface ProductRepository extends JpaRepository<Product, Long> {}

@Data
@Entity
class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // customer_seq tablosunuda olustrur
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING) // artik string degeri neyse onu yazar
    private CustomerType customerType; // db de 0,1,2 olarak tutulur

    private Metadata metadata;

    @OneToMany(cascade = CascadeType.ALL,mappedBy = "customer", fetch = FetchType.EAGER) // zincirleme veri varsa onlarida hallet demek
    //mappedby diger tabloda beni nasil aniyorlar diye
    private List<Product> products;
}

enum CustomerType{
    ELITE_CUSTOMER,
    SUPER_CUSTOMER,
    NORMAL_CUSTOMER
    //biri bunlarin yerini degistirirse db ye veri tutarsizligi olur o yuzden yukarida @Enumerated kullan
}


@Data
@Entity
class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private BigDecimal price;

    @ManyToOne()
    private Customer customer;

    @Formula("price * 0.18")
    private BigDecimal tax; //veritabanina yazmaz transient olarak algilar hibernate formula sayesinde


    private Metadata metadata;


}

@Embeddable //uzun uzun kullanimlari engeller daha temiz kod olur
@Data
class Metadata{

    private Timestamp createdAt;

    private Timestamp updatedAt;
}

@Service
@RequiredArgsConstructor
class CustomerService {
    private final CustomerRepository customerRepository;

    @Transactional
    public List<Customer> findAll() throws InterruptedException {
        Thread.sleep(5000);
        return customerRepository.findAll();
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
}