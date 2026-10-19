using System.ComponentModel.DataAnnotations;

namespace Student.DTOs
{
    public class PayTuitionRequest
    {
        [Required(ErrorMessage = "Transaction ID is required.")]
        [Range(1, long.MaxValue, ErrorMessage = "Transaction ID must be greater than 0.")]
        public long Transaction_ID { get; set; }
    }
}
