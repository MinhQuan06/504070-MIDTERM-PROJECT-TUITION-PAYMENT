using System.ComponentModel.DataAnnotations;

namespace Student.DTOs
{
    public class CreateTuitionRequest
    {
        [Required(ErrorMessage = "Student ID is required.")]
        [StringLength(10, MinimumLength = 1, ErrorMessage = "Student ID must be between 1 and 10 characters.")]
        public string Student_ID { get; set; } = string.Empty;

        [Required(ErrorMessage = "Semester is required.")]
        [StringLength(10, MinimumLength = 1, ErrorMessage = "Semester must be between 1 and 10 characters.")]
        public string Semester { get; set; } = string.Empty;

        [Range(1, long.MaxValue, ErrorMessage = "Amount must be greater than 0.")]
        public long Amount { get; set; }
    }
}