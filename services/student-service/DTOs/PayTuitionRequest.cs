using System.ComponentModel.DataAnnotations;

namespace Student.DTOs
{
    public class PayTuitionRequest
    {
        [Required(ErrorMessage = "Transaction ID is required.")]
        [Range(1, long.MaxValue, ErrorMessage = "Transaction ID must be greater than 0.")]
        public long Transaction_ID { get; set; }

        [Required, StringLength(10, MinimumLength = 1)]
        public string Student_ID { get; set; } = string.Empty;

        [Range(1, long.MaxValue)]
        public long Amount { get; set; }

        [Range(0, int.MaxValue)]
        public int? Expected_Version { get; set; }
    }
}
