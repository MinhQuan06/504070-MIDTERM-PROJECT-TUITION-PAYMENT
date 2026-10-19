using System.ComponentModel.DataAnnotations;

namespace Student.DTOs
{
    public class UpdateStudentRequest
    {
        [Required(ErrorMessage = "Full Name is required.")]
        [StringLength(100, MinimumLength = 1, ErrorMessage = "Full Name must be between 1 and 100 characters.")]
        public string Full_Name { get; set; } = string.Empty;
    }
}
