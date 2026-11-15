import 'format.dart';

class PageData<T> {
  PageData({required this.records, required this.total});

  final List<T> records;
  final int total;

  static PageData<T> from<T>(dynamic json, T Function(Map<String, dynamic>) parse) {
    if (json is Map) {
      final list = mapList(json['records'] ?? json['list']).map(parse).toList();
      return PageData(records: list, total: intOf(json['total'], list.length));
    }
    return PageData(records: const [], total: 0);
  }
}

class NamedOption {
  NamedOption(this.id, this.name, {this.extra});

  final int id;
  final String name;
  final Map<String, dynamic>? extra;

  factory NamedOption.from(Map<String, dynamic> json) =>
      NamedOption(intOf(json['id']), str(json['name']), extra: json);
}

class UserInfo {
  UserInfo({
    required this.staffId,
    required this.name,
    required this.role,
    required this.campusId,
    required this.permissions,
  });

  final int staffId;
  final String name;
  final String role;
  final int? campusId;
  final List<String> permissions;

  bool get isAdmin => permissions.contains('*');

  bool can(List<String> paths) => isAdmin || paths.any(permissions.contains);

  factory UserInfo.from(Map<String, dynamic> json) {
    final raw = json['permissions'];
    final permissions = raw is List ? raw.map((e) => '$e').toList() : <String>[];
    return UserInfo(
      staffId: intOf(json['staffId']),
      name: str(json['name'], '未命名'),
      role: str(json['role'], '员工'),
      campusId: json['campusId'] == null ? null : intOf(json['campusId']),
      permissions: permissions,
    );
  }
}

class Student {
  Student({
    required this.id,
    required this.name,
    required this.gender,
    required this.status,
    required this.phone,
    required this.campusName,
    required this.courseCount,
    required this.balance,
    required this.debt,
    required this.points,
    required this.isStar,
    required this.ageText,
    required this.grade,
  });

  final int id;
  final String name;
  final String gender;
  final String status;
  final String phone;
  final String campusName;
  final int courseCount;
  final double balance;
  final double debt;
  final int points;
  final int isStar;
  final String? ageText;
  final String? grade;

  factory Student.from(Map<String, dynamic> json) => Student(
        id: intOf(json['id']),
        name: str(json['name'], '未命名'),
        gender: str(json['gender']),
        status: str(json['status'], '在读'),
        phone: str(json['phone']),
        campusName: str(json['campusName'] ?? json['campusText'], '-'),
        courseCount: intOf(json['courseCount']),
        balance: doubleOf(json['balance']),
        debt: doubleOf(json['debt']),
        points: intOf(json['points']),
        isStar: intOf(json['isStar']),
        ageText: strOrNull(json['ageText']),
        grade: strOrNull(json['grade']),
      );
}

class StudentSummary {
  StudentSummary(this.count, this.balance, this.debt, this.points);

  final int count;
  final double balance;
  final double debt;
  final int points;

  factory StudentSummary.from(Map<String, dynamic> json) => StudentSummary(
        intOf(json['count']),
        doubleOf(json['balance']),
        doubleOf(json['debt']),
        intOf(json['points']),
      );
}

class Guardian {
  Guardian(this.name, this.relation, this.phone);

  final String name;
  final String? relation;
  final String phone;

  factory Guardian.from(Map<String, dynamic> json) =>
      Guardian(str(json['name'], '-'), strOrNull(json['relation']), str(json['phone']));
}

class Enrollment {
  Enrollment({
    required this.id,
    required this.courseName,
    required this.className,
    required this.teacher,
    required this.totalLessons,
    required this.giftLessons,
    required this.remainLessons,
    required this.usedLessons,
    required this.paidAmount,
    required this.consumedAmount,
    required this.unitPrice,
    required this.accountBalance,
    required this.status,
    required this.expireDate,
    required this.enrollTime,
  });

  final int id;
  final String courseName;
  final String? className;
  final String? teacher;
  final num totalLessons;
  final num giftLessons;
  final num remainLessons;
  final num usedLessons;
  final num paidAmount;
  final num consumedAmount;
  final num? unitPrice;
  final num accountBalance;
  final String status;
  final String? expireDate;
  final String? enrollTime;

  factory Enrollment.from(Map<String, dynamic> json) => Enrollment(
        id: intOf(json['id']),
        courseName: str(json['courseName'], '未命名课程'),
        className: strOrNull(json['className']),
        teacher: strOrNull(json['teacher']),
        totalLessons: numOrNull(json['totalLessons']) ?? 0,
        giftLessons: numOrNull(json['giftLessons']) ?? 0,
        remainLessons: numOrNull(json['remainLessons']) ?? 0,
        usedLessons: numOrNull(json['usedLessons']) ?? 0,
        paidAmount: numOrNull(json['paidAmount']) ?? 0,
        consumedAmount: numOrNull(json['consumedAmount']) ?? 0,
        unitPrice: numOrNull(json['unitPrice']),
        accountBalance: numOrNull(json['accountBalance']) ?? 0,
        status: str(json['status'], '在读'),
        expireDate: strOrNull(json['expireDate']),
        enrollTime: strOrNull(json['enrollTime']),
      );
}

class LessonTxn {
  LessonTxn(this.id, this.type, this.lessons, this.unitPrice, this.amount, this.balanceAfter, this.bizType, this.note, this.createdAt);

  final int id;
  final String type;
  final num lessons;
  final num? unitPrice;
  final num? amount;
  final num? balanceAfter;
  final String? bizType;
  final String? note;
  final String createdAt;

  bool get isAdd => type.contains('充值') || type.contains('增') || type.contains('退') || type.contains('转') || lessons > 0;

  factory LessonTxn.from(Map<String, dynamic> json) => LessonTxn(
        intOf(json['id']),
        str(json['type']),
        numOrNull(json['lessons']) ?? 0,
        numOrNull(json['unitPrice']),
        numOrNull(json['amount']),
        numOrNull(json['balanceAfter']),
        strOrNull(json['bizType']),
        strOrNull(json['note']),
        str(json['createdAt']),
      );
}

class StudentDetail {
  StudentDetail({required this.info, required this.guardians, required this.enrollments, required this.txns});

  final Map<String, dynamic> info;
  final List<Guardian> guardians;
  final List<Enrollment> enrollments;
  final List<LessonTxn> txns;

  factory StudentDetail.from(Map<String, dynamic> json) {
    final info = json['student'] is Map ? Map<String, dynamic>.from(json['student']) : <String, dynamic>{};
    return StudentDetail(
      info: info,
      guardians: mapList(json['guardians']).map(Guardian.from).toList(),
      enrollments: mapList(json['enrollments']).map(Enrollment.from).toList(),
      txns: mapList(json['txns']).map(LessonTxn.from).toList(),
    );
  }
}

class ClassInfo {
  ClassInfo({
    required this.id,
    required this.name,
    required this.courseName,
    required this.campusName,
    required this.teacherName,
    required this.assistName,
    required this.room,
    required this.scheduleText,
    required this.status,
    required this.capacity,
    required this.enrolledCount,
    required this.enrolling,
    required this.startDate,
    required this.endDate,
    required this.studentCount,
    required this.lessonCount,
    required this.attendanceRate,
  });

  final int id;
  final String name;
  final String? courseName;
  final String campusName;
  final String? teacherName;
  final String? assistName;
  final String? room;
  final String? scheduleText;
  final String status;
  final int capacity;
  final int enrolledCount;
  final bool enrolling;
  final String? startDate;
  final String? endDate;
  final int studentCount;
  final int lessonCount;
  final double? attendanceRate;

  factory ClassInfo.from(Map<String, dynamic> json) => ClassInfo(
        id: intOf(json['id']),
        name: str(json['name'], '未命名班级'),
        courseName: strOrNull(json['courseName']),
        campusName: str(json['campusName'], '-'),
        teacherName: strOrNull(json['teacherName']),
        assistName: strOrNull(json['assistName']),
        room: strOrNull(json['room']),
        scheduleText: strOrNull(json['scheduleText']),
        status: str(json['status'], '已开班'),
        capacity: intOf(json['capacity']),
        enrolledCount: intOf(json['enrolledCount']),
        enrolling: intOf(json['enrolling']) == 1,
        startDate: strOrNull(json['startDate']),
        endDate: strOrNull(json['endDate']),
        studentCount: intOf(json['studentCount'] ?? json['enrolledCount']),
        lessonCount: intOf(json['lessonCount']),
        attendanceRate: numOrNull(json['attendanceRate'])?.toDouble(),
      );
}

class ClassStudent {
  ClassStudent(this.id, this.studentId, this.studentName, this.gender, this.status, this.totalLessons, this.usedLessons, this.remainLessons, this.joinDate);

  final int id;
  final int studentId;
  final String studentName;
  final String? gender;
  final String status;
  final num totalLessons;
  final num usedLessons;
  final num remainLessons;
  final String? joinDate;

  factory ClassStudent.from(Map<String, dynamic> json) => ClassStudent(
        intOf(json['id']),
        intOf(json['studentId']),
        str(json['studentName'], '未命名'),
        strOrNull(json['gender']),
        str(json['status'], '在读'),
        numOrNull(json['totalLessons']) ?? 0,
        numOrNull(json['usedLessons']) ?? 0,
        numOrNull(json['remainLessons']) ?? 0,
        strOrNull(json['joinDate']),
      );
}

class ClassSchedule {
  ClassSchedule(this.id, this.lessonDate, this.startTime, this.endTime, this.topic, this.room, this.teacherName, this.status, this.shouldCount, this.actualCount, this.attendanceRate, this.commentCount);

  final int id;
  final String lessonDate;
  final String startTime;
  final String endTime;
  final String? topic;
  final String? room;
  final String? teacherName;
  final String status;
  final int shouldCount;
  final int actualCount;
  final double? attendanceRate;
  final int commentCount;

  factory ClassSchedule.from(Map<String, dynamic> json) => ClassSchedule(
        intOf(json['id']),
        str(json['lessonDate']),
        str(json['startTime']),
        str(json['endTime']),
        strOrNull(json['topic']),
        strOrNull(json['room']),
        strOrNull(json['teacherName']),
        str(json['status'], '待上课'),
        intOf(json['shouldCount']),
        intOf(json['actualCount']),
        numOrNull(json['attendanceRate'])?.toDouble(),
        intOf(json['commentCount']),
      );
}

class ClassPhoto {
  ClassPhoto(this.id, this.url, this.uploader, this.createdAt);

  final int id;
  final String url;
  final String? uploader;
  final String createdAt;

  factory ClassPhoto.from(Map<String, dynamic> json) =>
      ClassPhoto(intOf(json['id']), str(json['url']), strOrNull(json['uploader']), str(json['createdAt']));
}

class ScheduleItem {
  ScheduleItem({
    required this.id,
    required this.classId,
    required this.className,
    required this.courseName,
    required this.teacherName,
    required this.assistantName,
    required this.lessonDate,
    required this.startTime,
    required this.endTime,
    required this.room,
    required this.topic,
    required this.status,
    required this.shouldCount,
    required this.actualCount,
    required this.campusName,
  });

  final int id;
  final int? classId;
  final String? className;
  final String? courseName;
  final String? teacherName;
  final String? assistantName;
  final String lessonDate;
  final String startTime;
  final String endTime;
  final String? room;
  final String? topic;
  final String status;
  final int shouldCount;
  final int actualCount;
  final String campusName;

  factory ScheduleItem.from(Map<String, dynamic> json) => ScheduleItem(
        id: intOf(json['id']),
        classId: json['classId'] == null ? null : intOf(json['classId']),
        className: strOrNull(json['className']),
        courseName: strOrNull(json['courseName']),
        teacherName: strOrNull(json['teacherName']),
        assistantName: strOrNull(json['assistantName']),
        lessonDate: str(json['lessonDate']),
        startTime: str(json['startTime']),
        endTime: str(json['endTime']),
        room: strOrNull(json['room']),
        topic: strOrNull(json['topic']),
        status: str(json['status'], '待上课'),
        shouldCount: intOf(json['shouldCount']),
        actualCount: intOf(json['actualCount']),
        campusName: str(json['campusName'], '-'),
      );
}

class DashboardSummary {
  DashboardSummary.from(Map<String, dynamic> json)
      : students = intOf(json['students']),
        activeStudents = intOf(json['activeStudents']),
        remainLessons = numOrNull(json['remainLessons']) ?? 0,
        debt = doubleOf(json['debt']),
        revenue = doubleOf(json['revenue']),
        classes = intOf(json['classes']),
        todayClasses = intOf(json['todayClasses']),
        todayOrders = intOf(json['todayOrders']),
        topCourses = mapList(json['topCourses']);

  final int students;
  final int activeStudents;
  final num remainLessons;
  final double debt;
  final double revenue;
  final int classes;
  final int todayClasses;
  final int todayOrders;
  final List<Map<String, dynamic>> topCourses;
}

class OrderInfo {
  OrderInfo.from(Map<String, dynamic> json)
      : id = intOf(json['id']),
        orderNo = str(json['orderNo'], '-'),
        stuName = str(json['stuName'], '-'),
        stuTel = strOrNull(json['stuTel']),
        typeName = str(json['typeName'], '-'),
        campusName = str(json['campusName'], '-'),
        content = strOrNull(json['content']),
        shouldPay = doubleOf(json['shouldPay']),
        realPay = doubleOf(json['realPay']),
        remain = doubleOf(json['remain']),
        arrearage = doubleOf(json['arrearage']),
        credit = doubleOf(json['credit']),
        statusName = str(json['statusName'], '-'),
        statusCode = intOf(json['statusCode']),
        payMethod = strOrNull(json['payMethod']),
        salesman = strOrNull(json['salesman']),
        creator = strOrNull(json['creator']),
        sourceName = str(json['sourceName']),
        sourceType = intOf(json['sourceType'], -1),
        isReturn = json['isReturn'] == true,
        dealDate = str(json['dealDate']),
        createdTime = str(json['createdTime']),
        comment = strOrNull(json['comment']),
        voidReason = strOrNull(json['voidReason']),
        voidAt = strOrNull(json['voidAt']),
        hasTextbook = json['hasTextbook'] == true,
        expiryDate = strOrNull(json['expiryDate']);

  final int id;
  final String orderNo;
  final String stuName;
  final String? stuTel;
  final String typeName;
  final String campusName;
  final String? content;
  final double shouldPay;
  final double realPay;
  final double remain;
  final double arrearage;
  final double credit;
  final String statusName;
  final int statusCode;
  final String? payMethod;
  final String? salesman;
  final String? creator;
  final String sourceName;
  final int sourceType;
  final bool isReturn;
  final String dealDate;
  final String createdTime;
  final String? comment;
  final String? voidReason;
  final String? voidAt;
  final bool hasTextbook;
  final String? expiryDate;

  bool get isLocal => sourceName.contains('本地') || sourceName.contains('本系统');
}

class OrderSummary {
  OrderSummary(this.count, this.shouldPay, this.realPay, this.arrearage);

  final int count;
  final double shouldPay;
  final double realPay;
  final double arrearage;

  factory OrderSummary.from(Map<String, dynamic> json) => OrderSummary(
        intOf(json['count']),
        doubleOf(json['shouldPay']),
        doubleOf(json['realPay']),
        doubleOf(json['arrearage']),
      );
}

class OrderPage {
  OrderPage(this.records, this.total, this.summary);

  final List<OrderInfo> records;
  final int total;
  final OrderSummary summary;
}

class GradeExam {
  GradeExam.from(Map<String, dynamic> json)
      : id = intOf(json['id']),
        name = str(json['name'], '未命名考试'),
        type = str(json['type'], '考试'),
        courseId = json['courseId'] == null ? null : intOf(json['courseId']),
        courseName = strOrNull(json['courseName']),
        examDate = strOrNull(json['examDate']),
        fullScore = numOrNull(json['fullScore']) ?? 100,
        scoringMode = str(json['scoringMode'], '分数'),
        status = str(json['status'], '草稿'),
        classNames = (json['classNames'] is List) ? (json['classNames'] as List).map((e) => '$e').toList() : <String>[],
        classIds = intList(json['classIds']),
        studentCount = intOf(json['studentCount']),
        enteredCount = intOf(json['enteredCount']),
        creator = strOrNull(json['creator']),
        remark = strOrNull(json['remark']),
        paperImages = (json['paperImages'] is List) ? (json['paperImages'] as List).map((e) => '$e').toList() : <String>[],
        parts = mapList(json['parts']).map(GradePart.from).toList();

  final int id;
  final String name;
  final String type;
  final int? courseId;
  final String? courseName;
  final String? examDate;
  final num fullScore;
  final String scoringMode;
  final String status;
  final List<String> classNames;
  final List<int> classIds;
  final int studentCount;
  final int enteredCount;
  final String? creator;
  final String? remark;
  final List<String> paperImages;
  final List<GradePart> parts;

  bool get editable => status == '草稿';
}

/// 考试分项：weight 为权重（%），fullScore 为分项满分。
class GradePart {
  GradePart({required this.id, required this.name, this.weight, this.fullScore, this.sort = 0});

  GradePart.from(Map<String, dynamic> json)
      : id = intOf(json['id']),
        name = str(json['name'], '分项'),
        weight = numOrNull(json['weight']),
        fullScore = numOrNull(json['fullScore']),
        sort = intOf(json['sort']);

  final int id;
  final String name;
  final num? weight;
  final num? fullScore;
  final int sort;
}

class GradeAnalysis {
  GradeAnalysis.from(Map<String, dynamic> json)
      : examId = intOf(json['examId']),
        examName = str(json['examName'], '-'),
        examType = str(json['examType']),
        examDate = strOrNull(json['examDate']),
        fullScore = numOrNull(json['fullScore']) ?? 100,
        studentCount = intOf(json['studentCount']),
        enteredCount = intOf(json['enteredCount']),
        absentCount = intOf(json['absentCount']),
        avgScore = numOrNull(json['avgScore']),
        maxScore = numOrNull(json['maxScore']),
        minScore = numOrNull(json['minScore']),
        passRate = numOrNull(json['passRate']),
        excellentRate = numOrNull(json['excellentRate']),
        scoringMode = str(json['scoringMode'], '分数'),
        exemptCount = intOf(json['exemptCount']),
        missingCount = intOf(json['missingCount']),
        distributions = mapList(json['distributions']),
        classes = mapList(json['classes']),
        rankings = mapList(json['rankings']),
        levels = mapList(json['levels']);

  final int examId;
  final String examName;
  final String examType;
  final String? examDate;
  final num fullScore;
  final int studentCount;
  final int enteredCount;
  final int absentCount;
  final String scoringMode;
  final int exemptCount;
  final int missingCount;
  final num? avgScore;
  final num? maxScore;
  final num? minScore;
  final num? passRate;
  final num? excellentRate;
  final List<Map<String, dynamic>> distributions;
  final List<Map<String, dynamic>> classes;
  final List<Map<String, dynamic>> rankings;
  final List<Map<String, dynamic>> levels;
}

class GradeQueryRow {
  GradeQueryRow.from(Map<String, dynamic> json)
      : id = intOf(json['id']),
        examId = intOf(json['examId']),
        examName = str(json['examName'], '-'),
        examType = str(json['examType']),
        examDate = strOrNull(json['examDate']),
        fullScore = numOrNull(json['fullScore']) ?? 100,
        scoringMode = str(json['scoringMode'], '分数'),
        className = str(json['className'], '-'),
        studentId = intOf(json['studentId']),
        studentName = str(json['studentName'], '-'),
        totalScore = numOrNull(json['totalScore']),
        level = strOrNull(json['level']),
        status = str(json['status'], '-'),
        rankClass = json['rankClass'] == null ? null : intOf(json['rankClass']),
        rankCampus = json['rankCampus'] == null ? null : intOf(json['rankCampus']),
        remark = strOrNull(json['remark']);

  final int id;
  final int examId;
  final String examName;
  final String examType;
  final String? examDate;
  final num fullScore;
  final String scoringMode;
  final String className;
  final int studentId;
  final String studentName;
  final num? totalScore;
  final String? level;
  final String status;
  final int? rankClass;
  final int? rankCampus;
  final String? remark;
}

class MessageInfo {
  MessageInfo.from(Map<String, dynamic> json)
      : id = intOf(json['id']),
        title = str(json['title'], '-'),
        content = str(json['content']),
        msgType = str(json['msgType'], '系统通知'),
        audienceType = str(json['audienceType'], '全员'),
        classNameText = strOrNull(json['classNameText']),
        targetCount = intOf(json['targetCount']),
        receiverCount = intOf(json['receiverCount']),
        readCount = intOf(json['readCount']),
        sender = strOrNull(json['sender']),
        sentAt = str(json['sentAt']);

  final int id;
  final String title;
  final String content;
  final String msgType;
  final String audienceType;
  final String? classNameText;
  final int targetCount;
  final int receiverCount;
  final int readCount;
  final String? sender;
  final String sentAt;

  double get readRate => receiverCount == 0 ? 0 : readCount / receiverCount;
}

class HomeworkInfo {
  HomeworkInfo.from(Map<String, dynamic> json)
      : id = intOf(json['id']),
        classId = intOf(json['classId']),
        className = strOrNull(json['className']),
        teacherName = strOrNull(json['teacherName']),
        title = str(json['title'], '-'),
        content = str(json['content']),
        dueDate = strOrNull(json['dueDate']),
        publishDate = str(json['publishDate']),
        publisher = strOrNull(json['publisher']),
        status = str(json['status'], '-');

  final int id;
  final int classId;
  final String? className;
  final String? teacherName;
  final String title;
  final String content;
  final String? dueDate;
  final String publishDate;
  final String? publisher;
  final String status;
}

class SuspendInfo {
  SuspendInfo.from(Map<String, dynamic> json)
      : id = intOf(json['id']),
        scheduleId = intOf(json['scheduleId']),
        classId = intOf(json['classId']),
        className = strOrNull(json['className']),
        courseName = strOrNull(json['courseName']),
        teacherName = strOrNull(json['teacherName']),
        lessonDate = str(json['lessonDate']),
        startTime = strOrNull(json['startTime']),
        endTime = strOrNull(json['endTime']),
        reason = str(json['reason']),
        status = str(json['status'], '-'),
        notifyFlag = intOf(json['notifyFlag']),
        operatorName = strOrNull(json['operatorName']),
        suspendedAt = str(json['suspendedAt']),
        restoredAt = strOrNull(json['restoredAt']);

  final int id;
  final int scheduleId;
  final int classId;
  final String? className;
  final String? courseName;
  final String? teacherName;
  final String lessonDate;
  final String? startTime;
  final String? endTime;
  final String reason;
  final String status;
  final int notifyFlag;
  final String? operatorName;
  final String suspendedAt;
  final String? restoredAt;
}
