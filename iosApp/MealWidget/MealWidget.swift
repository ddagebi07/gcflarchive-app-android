import WidgetKit
import SwiftUI
import UIKit
import AppIntents

/// 홈 화면 위젯: 급식 (iPhone · iPad). One meal at a time so the menu never gets cut off:
/// 중식 by default, 석식 from 13:30 (after 점심시간); 주말이면 다음 월요일 중식.
/// The ‹ › buttons (iOS 17+) step through meals and days; after an hour it goes back to the default.
/// It reads the menu straight from the NEIS open API (no login, nothing shared with the app).

struct MealItem {
    let kind: String      // 중식, 석식 …
    let calories: String?
    let dishes: [String]
}

struct MealEntry: TimelineEntry {
    let date: Date
    let day: Date          // the day shown
    let meal: MealItem?
    /// Shown instead of a menu: no 급식 that day, or offline.
    let message: String?
}

enum NEIS {
    // Same values as the app (core Config.kt) and meal.html.
    static let key = "2087d61d2897469dbd2619d96224bbd0"
    static let office = "J10"         // 경기도교육청
    static let school = "7530145"     // 과천외국어고등학교
    static let seoul = TimeZone(identifier: "Asia/Seoul")!

    static var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = seoul
        return c
    }

    private static var formatter: DateFormatter {
        let f = DateFormatter()
        f.timeZone = seoul
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyyMMdd"
        return f
    }

    static func ymd(_ d: Date) -> String { formatter.string(from: d) }
    static func day(fromYMD s: String) -> Date? { formatter.date(from: s) }

    static func fetch(_ day: Date) async throws -> [MealItem] {
        var c = URLComponents(string: "https://open.neis.go.kr/hub/mealServiceDietInfo")!
        c.queryItems = [
            .init(name: "Type", value: "json"), .init(name: "pIndex", value: "1"), .init(name: "pSize", value: "10"),
            .init(name: "ATPT_OFCDC_SC_CODE", value: office), .init(name: "SD_SCHUL_CODE", value: school),
            .init(name: "MLSV_YMD", value: ymd(day)), .init(name: "KEY", value: key),
        ]
        let (data, _) = try await URLSession.shared.data(from: c.url!)
        // No 급식 comes back as {"RESULT":{"CODE":"INFO-200"}}: an empty list.
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let info = root["mealServiceDietInfo"] as? [[String: Any]],
              let rows = info.compactMap({ $0["row"] as? [[String: Any]] }).first else { return [] }
        return rows.map { row in
            let kind = (row["MMEAL_SC_NM"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "급식"
            let cal = (row["CAL_INFO"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            let dishes = ((row["DDISH_NM"] as? String) ?? "")
                .components(separatedBy: "<br/>")
                .map { $0.replacingOccurrences(of: #"\([0-9.]+\)"#, with: "", options: .regularExpression)
                    .trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
            return MealItem(kind: kind, calories: cal, dishes: dishes)
        }
    }
}

/// Which meal the widget shows: a day plus an index into that day's meals (-1 = the last one).
/// No stored slot (or one older than an hour) = the default meal. Kept in the extension's own defaults.
enum MealNav {
    private static let store = UserDefaults.standard
    private static let ttl: TimeInterval = 3600   // back to the default an hour after the last tap

    static func isSchoolDay(_ d: Date) -> Bool {
        let wd = NEIS.calendar.component(.weekday, from: d)
        return wd != 1 && wd != 7
    }

    static func shiftSchoolDay(_ d: Date, by step: Int) -> Date {
        var day = NEIS.calendar.date(byAdding: .day, value: step, to: d)!
        while !isSchoolDay(day) { day = NEIS.calendar.date(byAdding: .day, value: step, to: day)! }
        return day
    }

    /// 오늘 (주말이면 다음 월요일), 중식 — or 석식 from 13:30 on a school day.
    static func defaultSlot(now: Date = Date()) -> (day: Date, preferred: String) {
        let today = NEIS.calendar.startOfDay(for: now)
        if !isSchoolDay(today) { return (shiftSchoolDay(today, by: 1), "중식") }
        let c = NEIS.calendar.dateComponents([.hour, .minute], from: now)
        let afterLunch = (c.hour ?? 0) * 60 + (c.minute ?? 0) >= 13 * 60 + 30
        return (today, afterLunch ? "석식" : "중식")
    }

    /// The stored slot if it's fresh, else nil (use the default).
    static func current() -> (day: Date, index: Int)? {
        guard Date().timeIntervalSince1970 - store.double(forKey: "navAt") < ttl,
              let ymd = store.string(forKey: "navDay"), let day = NEIS.day(fromYMD: ymd) else { return nil }
        return (day, store.integer(forKey: "navIndex"))
    }

    static func save(day: Date, index: Int) {
        store.set(NEIS.ymd(day), forKey: "navDay")
        store.set(index, forKey: "navIndex")
        store.set(Date().timeIntervalSince1970, forKey: "navAt")
    }

    /// The slot on screen now, resolved against that day's meals. nil = offline.
    static func resolve() async -> (day: Date, meals: [MealItem], index: Int)? {
        if let slot = current() {
            guard let meals = try? await NEIS.fetch(slot.day) else { return nil }
            let i = meals.isEmpty ? 0 : (slot.index < 0 ? meals.count - 1 : min(slot.index, meals.count - 1))
            return (slot.day, meals, i)
        }
        let slot = defaultSlot()
        guard let meals = try? await NEIS.fetch(slot.day) else { return nil }
        let i = meals.firstIndex { $0.kind == slot.preferred } ?? (slot.preferred == "석식" ? max(meals.count - 1, 0) : 0)
        return (slot.day, meals, i)
    }

    /// ‹ › : the next / previous meal, crossing into the next / previous school day.
    static func step(forward: Bool) async {
        let now = await resolve()
        let day = now?.day ?? defaultSlot().day
        let count = now?.meals.count ?? 0
        let index = now?.index ?? 0
        if forward {
            if index + 1 < count { save(day: day, index: index + 1) } else { save(day: shiftSchoolDay(day, by: 1), index: 0) }
        } else {
            if index > 0 && count > 0 { save(day: day, index: index - 1) } else { save(day: shiftSchoolDay(day, by: -1), index: -1) }
        }
    }
}

@available(iOS 17.0, *)
struct MealStepIntent: AppIntent {
    static var title: LocalizedStringResource = "급식 넘기기"
    static var isDiscoverable: Bool = false

    @Parameter(title: "다음") var forward: Bool

    init() {}
    init(forward: Bool) { self.forward = forward }

    func perform() async throws -> some IntentResult {
        await MealNav.step(forward: forward)
        return .result()   // WidgetKit reloads the widget after an intent
    }
}

struct MealProvider: TimelineProvider {
    func placeholder(in context: Context) -> MealEntry {
        MealEntry(date: Date(), day: Date(), meal: MealItem(kind: "중식", calories: nil, dishes: ["잡곡밥", "된장국", "불고기", "김치"]), message: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (MealEntry) -> Void) {
        Task { completion(await load()) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MealEntry>) -> Void) {
        Task {
            let entry = await load()
            // Next refresh: at 13:30 (중식 → 석식), else in an hour; and just after midnight for a new day.
            let cal = NEIS.calendar
            let now = Date()
            let midnight = cal.startOfDay(for: cal.date(byAdding: .day, value: 1, to: now)!).addingTimeInterval(60)
            var next = min(now.addingTimeInterval(3600), midnight)
            if let switchAt = cal.date(bySettingHour: 13, minute: 30, second: 30, of: now), switchAt > now { next = min(next, switchAt) }
            completion(Timeline(entries: [entry], policy: .after(next)))
        }
    }

    private func load() async -> MealEntry {
        guard let shown = await MealNav.resolve() else {
            return MealEntry(date: Date(), day: MealNav.defaultSlot().day, meal: nil,
                             message: "급식 정보를 불러오지 못했어요. 인터넷에 연결되면 다시 보여요.")
        }
        if shown.meals.isEmpty { return MealEntry(date: Date(), day: shown.day, meal: nil, message: "이날은 급식이 없어요.") }
        return MealEntry(date: Date(), day: shown.day, meal: shown.meals[shown.index], message: nil)
    }
}

struct MealWidgetView: View {
    let entry: MealEntry
    @Environment(\.widgetFamily) private var family

    private var small: Bool { family == .systemSmall }

    /// '오늘 중식' / '내일 석식' / '10/20(월) 중식'
    private var title: String {
        let cal = NEIS.calendar
        let days = cal.dateComponents([.day], from: cal.startOfDay(for: Date()), to: cal.startOfDay(for: entry.day)).day ?? 0
        let c = cal.dateComponents([.month, .day, .weekday], from: entry.day)
        let dow = ["일", "월", "화", "수", "목", "금", "토"][(c.weekday ?? 1) - 1]
        let when = days == 0 ? "오늘" : days == 1 ? "내일" : "\(c.month ?? 0)/\(c.day ?? 0)(\(dow))"
        return [when, entry.meal?.kind].compactMap { $0 }.joined(separator: " ")
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(alignment: .leading, spacing: small ? 4 : 6) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(title).font(.system(size: small ? 14 : 15, weight: .bold)).lineLimit(1)
                    if !small, let cal = entry.meal?.calories {
                        Text(cal).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                if let message = entry.message {
                    Text(message).font(.system(size: 13)).foregroundStyle(.secondary)
                } else if let meal = entry.meal {
                    // One meal as running text: dishes joined with '·', wrapping naturally.
                    Text(meal.dishes.joined(separator: " · "))
                        .font(.system(size: small ? 13 : 14))
                        .lineSpacing(2)
                        .minimumScaleFactor(0.8)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

            if #available(iOSApplicationExtension 17.0, *) {
                HStack(spacing: 4) {
                    Button(intent: MealStepIntent(forward: false)) { arrow("chevron.left") }.buttonStyle(.plain)
                    Button(intent: MealStepIntent(forward: true)) { arrow("chevron.right") }.buttonStyle(.plain)
                }
            }
        }
        .widgetURL(URL(string: "gcflarchive://meal"))
        .widgetBackground()
    }

    private func arrow(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(.secondary)
            .frame(width: 26, height: 26)
            .background(Circle().fill(Color(UIColor.secondarySystemBackground)))
            .accessibilityLabel(name == "chevron.left" ? "이전 끼니" : "다음 끼니")
    }
}

private extension View {
    /// iOS 17 wants containerBackground; older systems use padding + background.
    @ViewBuilder func widgetBackground() -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            self.containerBackground(for: .widget) { Color(UIColor.systemBackground) }
        } else {
            self.padding().background(Color(UIColor.systemBackground))
        }
    }
}

@main
struct MealWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "MealWidget", provider: MealProvider()) { entry in
            MealWidgetView(entry: entry)
        }
        .configurationDisplayName("급식")
        .description("한 끼씩 급식 메뉴를 보고, ‹ ›로 다른 끼니와 날짜로 넘겨요.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
