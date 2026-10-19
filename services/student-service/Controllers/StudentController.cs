using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Student.Data;
using Student.DTOs;
using Student.Models;

namespace Student.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class StudentController : ControllerBase
    {
        private readonly StudentDbContext _context;

        public StudentController(StudentDbContext context)
        {
            _context = context;
        }

        /// <summary>
        /// Lấy danh sách tất cả sinh viên
        /// </summary>
        [HttpGet]
        public async Task<IActionResult> GetAllStudents()
        {
            var students = await _context.Students
                .AsNoTracking()
                .ToListAsync();

            return Ok(students);
        }

        /// <summary>
        /// Lấy thông tin sinh viên theo mã sinh viên
        /// </summary>
        [HttpGet("{id}")]
        public async Task<IActionResult> GetStudentById(string id)
        {
            var student = await _context.Students
                .AsNoTracking()
                .FirstOrDefaultAsync(s => s.Student_ID == id);

            if (student == null)
            {
                return NotFound(new { message = $"Student with ID '{id}' was not found." });
            }

            return Ok(student);
        }

        /// <summary>
        /// Thêm mới một sinh viên
        /// </summary>
        [HttpPost]
        public async Task<IActionResult> CreateStudent([FromBody] CreateStudentRequest request)
        {
            var exists = await _context.Students
                .AnyAsync(s => s.Student_ID == request.Student_ID);

            if (exists)
            {
                return Conflict(new { message = $"Student with ID '{request.Student_ID}' already exists." });
            }

            var student = new Models.Student
            {
                Student_ID = request.Student_ID,
                Full_Name = request.Full_Name
            };

            _context.Students.Add(student);
            await _context.SaveChangesAsync();

            return CreatedAtAction(
                nameof(GetStudentById),
                new { id = student.Student_ID },
                student);
        }

        /// <summary>
        /// Cập nhật thông tin sinh viên
        /// </summary>
        [HttpPut("{id}")]
        public async Task<IActionResult> UpdateStudent(string id, [FromBody] UpdateStudentRequest request)
        {
            var student = await _context.Students
                .FirstOrDefaultAsync(s => s.Student_ID == id);

            if (student == null)
            {
                return NotFound(new { message = $"Student with ID '{id}' was not found." });
            }

            student.Full_Name = request.Full_Name;

            await _context.SaveChangesAsync();

            return Ok(student);
        }

        /// <summary>
        /// Xóa sinh viên
        /// </summary>
        [HttpDelete("{id}")]
        public async Task<IActionResult> DeleteStudent(string id)
        {
            var student = await _context.Students
                .FirstOrDefaultAsync(s => s.Student_ID == id);

            if (student == null)
            {
                return NotFound(new { message = $"Student with ID '{id}' was not found." });
            }

            // Kiểm tra ràng buộc toàn vẹn: nếu đã có dữ liệu học phí thì không cho phép xóa
            var hasTuition = await _context.Tuitions
                .AnyAsync(t => t.Student_ID == id);

            if (hasTuition)
            {
                return Conflict(new
                {
                    message = $"Cannot delete student '{id}' because associated tuition records exist."
                });
            }

            _context.Students.Remove(student);
            await _context.SaveChangesAsync();

            return NoContent();
        }
    }
}