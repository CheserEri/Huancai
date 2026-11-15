import 'format.dart';
import 'models.dart';

/// 考试「适用班级」选项。
class GradeClassOption {
  GradeClassOption.from(Map<String, dynamic> json)
      : id = intOf(json['id']),
        name = str(json['name'], '未命名班级'),
        campusId = json['campusId'] == null ? null : intOf(json['campusId']),
        courseId = json['courseId'] == null ? null : intOf(json['courseId']),
        courseName = strOrNull(json['courseName']);

  final int id;
  final String name;
  final int? campusId;
  final int? courseId;
  final String? courseName;
}

/// 成绩录入网格中的一行学员成绩。
class GradeEntryStudent {
  GradeEntryStudent.from(Map<String, dynamic> json)
      : studentId = intOf(json['studentId']),
        studentName = str(json['studentName'], '未命名'),
        studentNo = strOrNull(json['studentNo']),
        enrollmentId = json['enrollmentId'] == null ? null : intOf(json['enrollmentId']),
        scoreId = json['scoreId'] == null ? null : intOf(json['scoreId']),
        totalScore = numOrNull(json['totalScore']),
        level = strOrNull(json['level']),
        status = str(json['status'], '未考'),
        remark = strOrNull(json['remark']),
        rankClass = json['rankClass'] == null ? null : intOf(json['rankClass']),
        partScores = _parsePartScores(json['partScores']);

  final int studentId;
  final String studentName;
  final String? studentNo;
  final int? enrollmentId;
  final int? scoreId;
  final num? totalScore;
  final String? level;
  final String status;
  final String? remark;
  final int? rankClass;

  /// key 为分项 id 的字符串形式。
  final Map<String, num?> partScores;

  static Map<String, num?> _parsePartScores(dynamic value) {
    if (value is! Map) return <String, num?>{};
    final result = <String, num?>{};
    value.forEach((key, item) => result['$key'] = numOrNull(item));
    return result;
  }
}

/// 成绩录入页数据：考试信息 + 班级 + 分项 + 学员名单。
class GradeEntryData {
  GradeEntryData.from(Map<String, dynamic> json)
      : examId = intOf(json['examId']),
        examName = str(json['examName'], '未命名考试'),
        examType = str(json['examType']),
        examDate = strOrNull(json['examDate']),
        scoringMode = str(json['scoringMode'], '分数'),
        fullScore = numOrNull(json['fullScore']) ?? 100,
        status = str(json['status'], '草稿'),
        classId = intOf(json['classId']),
        className = str(json['className'], '未命名班级'),
        parts = mapList(json['parts']).map(GradePart.from).toList(),
        students = mapList(json['students']).map(GradeEntryStudent.from).toList();

  final int examId;
  final String examName;
  final String examType;
  final String? examDate;
  final String scoringMode;
  final num fullScore;
  final String status;
  final int classId;
  final String className;
  final List<GradePart> parts;
  final List<GradeEntryStudent> students;

  bool get editable => status == '草稿';
  bool get levelMode => scoringMode == '等级';
}

/// 等级规则：分数区间 → 等级名称。
class GradeRuleInfo {
  GradeRuleInfo.from(Map<String, dynamic> json)
      : id = intOf(json['id']),
        name = str(json['name'], '-'),
        minScore = numOrNull(json['minScore']),
        maxScore = numOrNull(json['maxScore']),
        sort = intOf(json['sort']);

  final int id;
  final String name;
  final num? minScore;
  final num? maxScore;
  final int sort;
}

/// 分项模板：新建考试时可一键导入分项配置。
class GradePartTemplateInfo {
  GradePartTemplateInfo.from(Map<String, dynamic> json)
      : id = intOf(json['id']),
        name = str(json['name'], '-'),
        courseCategory = strOrNull(json['courseCategory']),
        parts = mapList(json['parts']).map(GradePart.from).toList();

  final int id;
  final String name;
  final String? courseCategory;
  final List<GradePart> parts;
}

/// 班级历次考试表现（成绩分析-班级趋势 / 班级详情成绩页签）。
class GradeClassExam {
  GradeClassExam.from(Map<String, dynamic> json)
      : examId = intOf(json['examId']),
        examName = str(json['examName'], '未命名考试'),
        examType = str(json['examType']),
        examDate = strOrNull(json['examDate']),
        fullScore = numOrNull(json['fullScore']) ?? 100,
        scoringMode = str(json['scoringMode'], '分数'),
        status = str(json['status'], '草稿'),
        enteredCount = intOf(json['enteredCount']),
        studentCount = intOf(json['studentCount']),
        avgScore = numOrNull(json['avgScore']),
        maxScore = numOrNull(json['maxScore']),
        minScore = numOrNull(json['minScore']),
        passRate = numOrNull(json['passRate']),
        excellentRate = numOrNull(json['excellentRate']);

  final int examId;
  final String examName;
  final String examType;
  final String? examDate;
  final num fullScore;
  final String scoringMode;
  final String status;
  final int enteredCount;
  final int studentCount;
  final num? avgScore;
  final num? maxScore;
  final num? minScore;
  final num? passRate;
  final num? excellentRate;

  /// 得分率（平均分 / 满分）。
  double? get scoreRate => avgScore == null || fullScore == 0 ? null : avgScore! * 100 / fullScore;
}

/// 单个学员的历次成绩（成绩分析-学员波动 / 学员详情成绩页签）。
class GradeStudentScore {
  GradeStudentScore.from(Map<String, dynamic> json)
      : examId = intOf(json['examId']),
        examName = str(json['examName'], '未命名考试'),
        examType = str(json['examType']),
        examDate = strOrNull(json['examDate']),
        fullScore = numOrNull(json['fullScore']) ?? 100,
        scoringMode = str(json['scoringMode'], '分数'),
        classId = json['classId'] == null ? null : intOf(json['classId']),
        className = strOrNull(json['className']),
        totalScore = numOrNull(json['totalScore']),
        level = strOrNull(json['level']),
        status = str(json['status'], '-'),
        rankClass = json['rankClass'] == null ? null : intOf(json['rankClass']),
        rankCampus = json['rankCampus'] == null ? null : intOf(json['rankCampus']),
        classAvgScore = numOrNull(json['classAvgScore']),
        remark = strOrNull(json['remark']);

  final int examId;
  final String examName;
  final String examType;
  final String? examDate;
  final num fullScore;
  final String scoringMode;
  final int? classId;
  final String? className;
  final num? totalScore;
  final String? level;
  final String status;
  final int? rankClass;
  final int? rankCampus;
  final num? classAvgScore;
  final String? remark;

  double? get scoreRate => totalScore == null || fullScore == 0 ? null : totalScore! * 100 / fullScore;
  double? get classRate => classAvgScore == null || fullScore == 0 ? null : classAvgScore! * 100 / fullScore;
}
