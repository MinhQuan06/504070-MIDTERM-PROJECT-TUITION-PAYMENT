package vn.edu.tdtu.ibanking.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "balance", nullable = false)
    private Long balance;

    @Version // Hỗ trợ Optimistic Locking
    @Column(name = "version", nullable = false)
    private Integer version;
}