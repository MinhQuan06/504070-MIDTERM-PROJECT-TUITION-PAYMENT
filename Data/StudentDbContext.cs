using Microsoft.EntityFrameworkCore;
using Student.Models;

namespace Student.Data
{
    public class StudentDbContext : DbContext
    {
        public StudentDbContext(
            DbContextOptions<StudentDbContext> options)
            : base(options)
        {
        }

        public DbSet<Models.Student> Students { get; set; } = null!;

        public DbSet<Tuition> Tuitions { get; set; } = null!;

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);

            modelBuilder.Entity<Models.Student>(entity =>
            {
                entity.ToTable("students");

                entity.HasKey(s => s.Student_ID);

                entity.Property(s => s.Student_ID)
                    .HasColumnName("student_id")
                    .HasMaxLength(10);

                entity.Property(s => s.Full_Name)
                    .HasColumnName("full_name")
                    .HasMaxLength(100);
            });

            modelBuilder.Entity<Tuition>(entity =>
            {
                entity.ToTable("tuitions");

                entity.HasKey(t => t.Tuition_ID);

                entity.Property(t => t.Tuition_ID)
                    .HasColumnName("tuition_id")
                    .ValueGeneratedOnAdd();

                entity.Property(t => t.Student_ID)
                    .HasColumnName("student_id")
                    .HasMaxLength(10);

                entity.Property(t => t.Semester)
                    .HasColumnName("semester")
                    .HasMaxLength(10);

                entity.Property(t => t.Amount)
                    .HasColumnName("amount");

                entity.Property(t => t.Status)
                    .HasColumnName("status")
                    .HasMaxLength(20);

                entity.Property(t => t.Version)
                    .HasColumnName("version")
                    .IsConcurrencyToken();

                entity.Property(t => t.Paid_By_Transaction_ID)
                    .HasColumnName("paid_by_transaction_id");
            });
        }
    }
}
