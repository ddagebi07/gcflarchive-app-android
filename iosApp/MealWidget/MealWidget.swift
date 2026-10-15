import WidgetKit
import SwiftUI
import UIKit

/// 홈 화면 위젯: 오늘의 급식 (iPhone · iPad). It reads the menu straight from the NEIS open API
/// (the same request the app makes, no login), so it needs nothing shared with the app.
/// Refreshes about every hour and right after midnight; tapping opens the 급식 tab.

struct MealItem: Identifiable {
    let id = UUID()
    let kind: String      // 중식, 석식 …
    let dishes: [String]
}

struct MealEntry: TimelineEntry {
    let date: Date
    let meals: [MealItem]
    /// Shown instead of the menu: no 급식 that day, or offline.
    let message: String?
}

enum NEIS {
    // Same values as the app (core Config.kt) and meal.html.
    static let key = "2087d61d2897469dbd2619d96224bbd0"
    static let office = "J10"         // 경기도교육청
    static let school = "7530145"     // 과천외국어고등학교
    static let seoul = TimeZone(identifier: "Asia/Seoul")!

    static func ymd(_ d: Date) -> String {
        let f = DateFormatter()
        f.timeZone = seoul
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyyMMdd"
        return f.string(from: d)
    }

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
            let dishes = ((row["DDISH_NM"] as? String) ?? "")
                .components(separatedBy: "<br/>")
                .map { $0.replacingOccurrences(of: #"\([0-9.]+\)"#, with: "", options: .regularExpression)
                    .trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
            return MealItem(kind: kind, dishes: dishes)
        }
    }
}

struct MealProvider: TimelineProvider {
    func placeholder(in context: Context) -> MealEntry {
        MealEntry(date: Date(), meals: [MealItem(kind: "중식", dishes: ["잡곡밥", "된장국", "불고기", "김치"])], message: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (MealEntry) -> Void) {
        Task { completion(await load()) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MealEntry>) -> Void) {
        Task {
            let entry = await load()
            // Next refresh: in an hour, or just after midnight if that comes first (new day's menu).
            var cal = Calendar(identifier: .gregorian)
            cal.timeZone = NEIS.seoul
            let midnight = cal.startOfDay(for: cal.date(byAdding: .day, value: 1, to: Date())!).addingTimeInterval(60)
            let next = min(Date().addingTimeInterval(3600), midnight)
            completion(Timeline(entries: [entry], policy: .after(next)))
        }
    }

    private func load() async -> MealEntry {
        do {
            let meals = try await NEIS.fetch(Date())
            return MealEntry(date: Date(), meals: meals, message: meals.isEmpty ? "오늘은 급식이 없어요." : nil)
        } catch {
            return MealEntry(date: Date(), meals: [], message: "급식 정보를 불러오지 못했어요. 인터넷에 연결되면 다시 보여요.")
        }
    }
}

struct MealWidgetView: View {
    let entry: MealEntry
    @Environment(\.widgetFamily) private var family

    private var title: String {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = NEIS.seoul
        let c = cal.dateComponents([.month, .day, .weekday], from: entry.date)
        let dow = ["일", "월", "화", "수", "목", "금", "토"][(c.weekday ?? 1) - 1]
        return "오늘의 급식 · \(c.month ?? 0)/\(c.day ?? 0)(\(dow))"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.system(size: 15, weight: .bold)).lineLimit(1)
            if let message = entry.message {
                Text(message).font(.system(size: 13)).foregroundStyle(.secondary)
            } else {
                // Small widget: one meal (석식 from 14:00, else 중식). Medium/large: every meal.
                ForEach(shown) { meal in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(meal.kind).font(.system(size: 13, weight: .bold)).foregroundStyle(Color(red: 0.19, green: 0.51, blue: 0.96))
                        Text(meal.dishes.joined(separator: " · ")).font(.system(size: 13))
                            .lineLimit(family == .systemSmall ? 6 : 3)
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetURL(URL(string: "gcflarchive://meal"))
        .widgetBackground()
    }

    private var shown: [MealItem] {
        guard family == .systemSmall else { return entry.meals }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = NEIS.seoul
        let preferred = cal.component(.hour, from: Date()) >= 14 ? "석식" : "중식"
        return [entry.meals.first { $0.kind == preferred } ?? entry.meals.first].compactMap { $0 }
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
        .configurationDisplayName("오늘의 급식")
        .description("오늘의 급식 메뉴를 홈 화면에서 바로 봐요.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
