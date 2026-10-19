using System.ComponentModel.DataAnnotations;

namespace Student.Models
{
    public class Tuition
    {
        public long Tuition_ID { get; set; }

        [Required(ErrorMessage = "Student ID is required.")]
        [StringLength(10, MinimumLength = 1)]
        public string Student_ID { get; set; } = string.Empty;

        [Required(ErrorMessage = "Semester is required.")]
        [StringLength(10, MinimumLength = 1)]
        public string Semester { get; set; } = string.Empty;

        [Range(1, long.MaxValue,
            ErrorMessage = "Amount must be greater than 0.")]
        public long Amount { get; set; }

        public string Status { get; set; } = "UNPAID";

        public int Version { get; set; }

        public long? Paid_By_Transaction_ID { get; set; }
    }
}