using System.ComponentModel.DataAnnotations;

namespace Student.DTOs
{
    public class CreateStudentRequest
    {
        [Required(ErrorMessage = "Student ID is required.")]
        [StringLength(10, MinimumLength = 1, ErrorMessage = "Student ID must be between 1 and 10 characters.")]
        public string Student_ID { get; set; } = string.Empty;

        [Required(ErrorMessage = "Full Name is required.")]
        [StringLength(100, MinimumLength = 1, ErrorMessage = "Full Name must be between 1 and 100 characters.")]
        public string Full_Name { get; set; } = string.Empty;
    }
}
