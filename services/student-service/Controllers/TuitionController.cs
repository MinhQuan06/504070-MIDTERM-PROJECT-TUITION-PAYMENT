using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Student.Data;
using Student.DTOs;
using Student.Models;

namespace Student.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class TuitionController : ControllerBase
    {
        private readonly StudentDbContext _context;
        private readonly IConfiguration _configuration;

        public TuitionController(StudentDbContext context, IConfiguration configuration)
        {
            _context = context;
            _configuration = configuration;
        }

        /// <summary>
        /// Lấy danh sách tất cả học phí
        /// </summary>
        [HttpGet]
        public async Task<IActionResult> GetAllTuitions()
        {
            var tuitions = await _context.Tuitions
                .AsNoTracking()
                .ToListAsync();

            return Ok(tuitions);
        }

        /// <summary>
        /// Lấy chi tiết học phí theo ID
        /// </summary>
        [HttpGet("{id}")]
        public async Task<IActionResult> GetTuitionById(long id)
        {
            var tuition = await _context.Tuitions
                .AsNoTracking()
                .FirstOrDefaultAsync(t => t.Tuition_ID == id);

            if (tuition == null)
            {
                return NotFound(new { message = $"Tuition record with ID {id} was not found." });
            }

            return Ok(tuition);
        }

        /// <summary>
        /// Lấy danh sách học phí của một sinh viên
        /// </summary>
        [HttpGet("student/{studentId}")]
        public async Task<IActionResult> GetTuitionsByStudentId(string studentId)
        {
            var studentExists = await _context.Students
                .AsNoTracking()
                .AnyAsync(s => s.Student_ID == studentId);

            if (!studentExists)
            {
                return NotFound(new { message = $"Student with ID '{studentId}' was not found." });
            }

            var tuitions = await _context.Tuitions
                .AsNoTracking()
                .Where(t => t.Student_ID == studentId)
                .ToListAsync();

            return Ok(tuitions);
        }

        /// <summary>
        /// Tạo mới một khoản học phí
        /// </summary>
        [HttpPost]
        public async Task<IActionResult> CreateTuition([FromBody] CreateTuitionRequest request)
        {
            var studentExists = await _context.Students
                .AnyAsync(s => s.Student_ID == request.Student_ID);

            if (!studentExists)
            {
                return BadRequest(new { message = $"Student '{request.Student_ID}' does not exist." });
            }

            var tuition = new Tuition
            {
                Student_ID = request.Student_ID,
                Semester = request.Semester,
                Amount = request.Amount,
                Status = "UNPAID",
                Version = 0,
                Paid_By_Transaction_ID = null
            };

            _context.Tuitions.Add(tuition);
            await _context.SaveChangesAsync();

            return CreatedAtAction(
                nameof(GetTuitionById),
                new { id = tuition.Tuition_ID },
                tuition);
        }

        /// <summary>
        /// Cập nhật thông tin học phí (khi chưa thanh toán)
        /// </summary>
        [HttpPut("{id}")]
        public async Task<IActionResult> UpdateTuition(long id, [FromBody] UpdateTuitionRequest request)
        {
            var tuition = await _context.Tuitions
                .FirstOrDefaultAsync(t => t.Tuition_ID == id);

            if (tuition == null)
            {
                return NotFound(new { message = $"Tuition record with ID {id} was not found." });
            }

            if (tuition.Status == "PAID")
            {
                return Conflict(new { message = "Cannot update tuition that has already been paid." });
            }

            var studentExists = await _context.Students
                .AnyAsync(s => s.Student_ID == request.Student_ID);

            if (!studentExists)
            {
                return BadRequest(new { message = $"Student '{request.Student_ID}' does not exist." });
            }

            tuition.Student_ID = request.Student_ID;
            tuition.Semester = request.Semester;
            tuition.Amount = request.Amount;
            tuition.Version++;

            try
            {
                await _context.SaveChangesAsync();
            }
            catch (DbUpdateConcurrencyException)
            {
                return Conflict(new
                {
                    message = "Tuition was modified by another request. Please reload and try again."
                });
            }

            return Ok(tuition);
        }

        /// <summary>
        /// Thanh toán học phí (sử dụng Optimistic Concurrency Control)
        /// </summary>
        [HttpPost("{id}/pay")]
        public async Task<IActionResult> PayTuition(long id, [FromBody] PayTuitionRequest request, [FromHeader(Name = "X-Internal-API-Key")] string? providedKey)
        {
            var key = _configuration["Payment:InternalApiKey"];
            if (string.IsNullOrEmpty(key)) return StatusCode(503, new { message = "Internal authentication is not configured." });
            if (providedKey == null || !System.Security.Cryptography.CryptographicOperations.FixedTimeEquals(
                System.Text.Encoding.UTF8.GetBytes(key), System.Text.Encoding.UTF8.GetBytes(providedKey)))
                return Unauthorized(new { message = "Invalid internal service credentials." });

            if (request.Transaction_ID <= 0)
            {
                return BadRequest(new { message = "Invalid transaction ID. Must be greater than 0." });
            }

            var tuition = await _context.Tuitions
                .FirstOrDefaultAsync(t => t.Tuition_ID == id);

            if (tuition == null)
            {
                return NotFound(new { message = $"Tuition record with ID {id} was not found." });
            }

            // Validate the immutable request snapshot, including on replay.
            if (tuition.Student_ID != request.Student_ID || tuition.Amount != request.Amount)
                return Conflict(new { message = "Tuition student or amount no longer matches the payment." });

            if (tuition.Status == "PAID")
            {
                if (tuition.Paid_By_Transaction_ID == request.Transaction_ID)
                    return Ok(new { message = "Payment already applied.", data = tuition });
                return Conflict(new { message = "Tuition was paid by another transaction." });
            }
            if (request.Expected_Version.HasValue && tuition.Version != request.Expected_Version.Value)
                return Conflict(new { message = "Tuition version changed. Reload and create a new payment." });

            tuition.Status = "PAID";
            tuition.Paid_By_Transaction_ID = request.Transaction_ID;
            tuition.Version++;

            try
            {
                await _context.SaveChangesAsync();
            }
            catch (DbUpdateConcurrencyException)
            {
                // A concurrent replay of the same operation is also successful.
                _context.ChangeTracker.Clear();
                var current = await _context.Tuitions.AsNoTracking().FirstOrDefaultAsync(t => t.Tuition_ID == id);
                if (current?.Status == "PAID" && current.Paid_By_Transaction_ID == request.Transaction_ID
                    && current.Student_ID == request.Student_ID && current.Amount == request.Amount)
                    return Ok(new { message = "Payment already applied.", data = current });
                return Conflict(new { message = "Tuition changed or was paid by another transaction." });
            }

            return Ok(new
            {
                message = "Tuition paid successfully.",
                data = tuition
            });
        }

        /// <summary>
        /// Xóa bản ghi học phí
        /// </summary>
        [HttpDelete("{id}")]
        public async Task<IActionResult> DeleteTuition(long id)
        {
            var tuition = await _context.Tuitions
                .FirstOrDefaultAsync(t => t.Tuition_ID == id);

            if (tuition == null)
            {
                return NotFound(new { message = $"Tuition record with ID {id} was not found." });
            }

            if (tuition.Status == "PAID")
            {
                return Conflict(new { message = "Cannot delete a tuition record that has already been paid." });
            }

            _context.Tuitions.Remove(tuition);
            await _context.SaveChangesAsync();

            return NoContent();
        }
    }
}