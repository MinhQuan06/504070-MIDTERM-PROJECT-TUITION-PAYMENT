using System.ComponentModel.DataAnnotations;

namespace Student.Models
{
    public class Student
    {
        [Key]
        [Required]
        [StringLength(10)]
        public string Student_ID { get; set; } = string.Empty;

        [Required]
        [StringLength(100)]
        public string Full_Name { get; set; } = string.Empty;
    }
}