package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.dto.WordDTO;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.Word;
import com.example.wordrecommend_backend.entity.WordState;
import com.example.wordrecommend_backend.repository.WordRepository;
import com.example.wordrecommend_backend.repository.WordStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service  // 標示這是 Spring 的服務層元件，會被自動掃描並註冊為 Bean
@RequiredArgsConstructor  // Lombok 註解：自動產生包含所有 final 欄位的建構子（用於依賴注入）
public class RecommendationService {

    // 注入 Word 資料表的存取介面
    private final WordRepository wordRepository;
    // 注入 WordState 資料表的存取介面（記錄使用者對每個單字的學習狀態）
    private final WordStateRepository wordStateRepository;

    /**
     * 核心方法：為使用者推薦單字
     * @param user 目標使用者
     * @param limit 需要推薦的單字數量
     * @return 推薦的單字列表（包含狀態資訊）
     */
    @Transactional(readOnly = true)  // 標示為唯讀交易，優化資料庫效能
    public List<WordDTO> getWordRecommendations(User user, int limit) {
        // 如果請求數量 <= 0，直接回傳空列表
        if (limit <= 0) return Collections.emptyList();

        // ========== 步驟 1：統計使用者的學習狀態 ==========
        // 計算使用者在 S1 狀態的單字數量（剛學習）
        long countS1 = wordStateRepository.countByUserAndState(user, "S1");
        // 計算使用者在 S2 狀態的單字數量（複習中）
        long countS2 = wordStateRepository.countByUserAndState(user, "S2");
        // 計算使用者在 S3 狀態的單字數量（熟練）
        long countS3 = wordStateRepository.countByUserAndState(user, "S3");
        // 計算總共學過多少單字（不包含 S0 新單字）
        double totalLearned = countS1 + countS2 + countS3;

        // ========== 步驟 2：根據學習進度決定狀態比例 ==========
        // 使用 LinkedHashMap 保持插入順序
        Map<String, Double> stateRatio = new LinkedHashMap<>();

        // 如果學過的單字少於 50 個（新手階段）
        if (totalLearned < 50) {
            stateRatio.put("S0", 1.0);  // 100% 推薦新單字
            stateRatio.put("S1", 0.0);  // 0% 複習 S1
            stateRatio.put("S2", 0.0);  // 0% 複習 S2
            stateRatio.put("S3", 0.0);  // 0% 複習 S3
        } else {
            // 進階階段：新單字 + 複習混合
            stateRatio.put("S0", 0.5);  // 50% 新單字
            stateRatio.put("S1", 0.2);  // 20% 複習 S1（剛學的）
            stateRatio.put("S2", 0.2);  // 20% 複習 S2（複習中的）
            stateRatio.put("S3", 0.1);  // 10% 複習 S3（熟練的）
        }

        // ========== 步驟 3：使用最大餘數法分配各狀態的配額 ==========
        // 將 limit 個單字按照 stateRatio 比例分配給各狀態
        Map<String, Integer> stateCounts = distributeCounts(limit, stateRatio);
        // 取得 S0（新單字）應分配的數量，如果 Map 中沒有則預設為 0
        int numS0 = stateCounts.getOrDefault("S0", 0);
        // 取得 S1（剛學習）應分配的數量
        int numS1 = stateCounts.getOrDefault("S1", 0);
        // 取得 S2（複習中）應分配的數量
        int numS2 = stateCounts.getOrDefault("S2", 0);
        // 取得 S3（熟練）應分配的數量
        int numS3 = stateCounts.getOrDefault("S3", 0);

        // ========== 步驟 4：動態調整 S0 新單字的難度等級比例 ==========
        // 計算學習進度（0.0 ~ 1.0），最多到 300 個單字就算 100%
        double progress = Math.min(totalLearned / 300.0, 1.0);

        // 根據進度調整各難度等級的比例（進度越高，越推薦高難度）
        Map<String, Double> levelRatio = new LinkedHashMap<>();
        levelRatio.put("A1", 0.30 - 0.20 * progress);  // A1：30% → 10%（隨進度降低）
        levelRatio.put("A2", 0.25 - 0.15 * progress);  // A2：25% → 10%
        levelRatio.put("B1", 0.20 - 0.05 * progress);  // B1：20% → 15%
        levelRatio.put("B2", 0.15 - 0.05 * progress);  // B2：15% → 10%
        levelRatio.put("C1", 0.07 + 0.25 * progress);  // C1：7%  → 32%（隨進度提高）
        levelRatio.put("C2", 0.03 + 0.20 * progress);  // C2：3%  → 23%（隨進度提高）

        // 將 S0 的配額再按難度等級分配
        Map<String, Integer> s0LevelCounts = distributeCounts(numS0, levelRatio);

        // ========== 步驟 5：從資料庫取出各類單字 ==========

        // 5.1 取 S0 新單字（按難度等級分別取）
        List<Word> s0Words = new ArrayList<>();
        // 遍歷每個難度等級及其配額
        for (Map.Entry<String, Integer> e : s0LevelCounts.entrySet()) {
            int take = e.getValue();  // 該難度等級應取的數量
            if (take <= 0) continue;  // 如果配額為 0，跳過
            // 從資料庫查詢該難度等級的新單字，並加入列表
            s0Words.addAll(wordRepository.findNewWordsByLevel(user, e.getKey(), page(take)));
        }

        // 5.2 取 S1 單字（剛學習的單字）
        List<Word> s1Words = numS1 > 0  // 如果 S1 配額 > 0
                ? wordStateRepository.findByUserAndState(user, "S1", page(numS1))  // 查詢 S1 單字
                .stream()  // 轉成 Stream
                .map(WordState::getWord)  // 從 WordState 取出 Word 物件
                .collect(Collectors.toList())  // 收集成 List
                : new ArrayList<>();  // 否則回傳空列表

        // 5.3 取 S2 單字（複習中的單字）
        List<Word> s2Words = numS2 > 0
                ? wordStateRepository.findByUserAndState(user, "S2", page(numS2))
                .stream().map(WordState::getWord).collect(Collectors.toList())
                : new ArrayList<>();

        // 5.4 取 S3 單字（熟練的單字）
        List<Word> s3Words = numS3 > 0
                ? wordStateRepository.findByUserAndState(user, "S3", page(numS3))
                .stream().map(WordState::getWord).collect(Collectors.toList())
                : new ArrayList<>();

        // ========== 步驟 6：合併所有單字並去重 ==========

        // 預先分配足夠的空間，避免動態擴容
        List<Word> merged = new ArrayList<>(s0Words.size() + s1Words.size() + s2Words.size() + s3Words.size());
        // 按順序加入各狀態的單字
        merged.addAll(s0Words);
        merged.addAll(s1Words);
        merged.addAll(s2Words);
        merged.addAll(s3Words);
        // 去除重複的單字（根據 ID）
        List<Word> deduped = deduplicateById(merged);

        // 如果去重後數量不足，用隨機新單字補充
        if (deduped.size() < limit) {
            int missing = limit - deduped.size();  // 計算還缺多少
            // 隨機取新單字
            for (Word w : wordRepository.findNewWordsRandomly(user, page(missing))) {
                // 檢查是否已存在（避免重複）
                if (deduped.stream().noneMatch(x -> Objects.equals(x.getId(), w.getId()))) {
                    deduped.add(w);  // 加入新單字
                    if (deduped.size() == limit) break;  // 達到目標數量就停止
                }
            }
        }

        // 嚴格截斷至 limit（保險措施，防止超出）
        if (deduped.size() > limit) {
            deduped = new ArrayList<>(deduped.subList(0, limit));  // 只保留前 limit 個
        }

        // ========== 步驟 7：為每個單字標記狀態，並轉換成 DTO ==========

        // 建立單字 ID → 狀態的對應表
        Map<Long, String> stateMap = new HashMap<>();
        // 標記 S0 單字
        s0Words.forEach(w -> stateMap.put(w.getId(), "S0"));
        // 標記 S1 單字
        s1Words.forEach(w -> stateMap.put(w.getId(), "S1"));
        // 標記 S2 單字
        s2Words.forEach(w -> stateMap.put(w.getId(), "S2"));
        // 標記 S3 單字
        s3Words.forEach(w -> stateMap.put(w.getId(), "S3"));
        // 為所有未標記的單字設定預設狀態 S0（補充的隨機單字）
        deduped.forEach(w -> stateMap.putIfAbsent(w.getId(), "S0"));

        // 隨機打亂單字順序（讓使用者不會看到固定順序）
        Collections.shuffle(deduped);

        // 將 Word 實體轉換成 WordDTO，並附帶狀態資訊
        return deduped.stream()
                .map(w -> WordDTO.fromEntityWithState(w, stateMap.getOrDefault(w.getId(), "S0")))
                .collect(Collectors.toList());
    }

    /**
     * 最大餘數法：按比例分配整數配額
     * 確保分配後的總和 = total（避免四捨五入造成的誤差）
     *
     * @param total 總共要分配的數量
     * @param ratios 各項目的比例（key=項目名稱, value=比例值）
     * @return 各項目分配到的整數數量
     */
    private Map<String, Integer> distributeCounts(int total, Map<String, Double> ratios) {
        // 使用 LinkedHashMap 保持插入順序
        Map<String, Integer> result = new LinkedHashMap<>();

        // 邊界檢查：如果總數 <= 0 或比例為空
        if (total <= 0 || ratios == null || ratios.isEmpty()) {
            // 將所有項目設為 0
            if (ratios != null) ratios.keySet().forEach(k -> result.put(k, 0));
            return result;
        }

        // 計算所有比例的總和
        double sum = ratios.values().stream().mapToDouble(Double::doubleValue).sum();

        // 如果比例總和 <= 0，無法分配
        if (sum <= 0) {
            ratios.keySet().forEach(k -> result.put(k, 0));
            return result;
        }

        // 內部類別：用來記錄每個項目的小數餘數
        class Part {
            String key;   // 項目名稱
            double frac;  // 小數餘數
            Part(String k, double f) {
                key = k;
                frac = f;
            }
        }

        List<Part> fracs = new ArrayList<>();  // 存放所有餘數資訊
        int allocated = 0;  // 已分配的總數

        // ========== 階段一：分配整數部分 ==========
        for (Map.Entry<String, Double> e : ratios.entrySet()) {
            // 計算該項目的精確配額（可能有小數）
            double exact = total * (e.getValue() / sum);

            // 向下取整，得到整數部分
            int base = (int) Math.floor(exact);

            // 計算小數餘數
            double rem = exact - base;

            // 先分配整數部分
            result.put(e.getKey(), base);

            // 記錄餘數（後續用來分配剩餘配額）
            fracs.add(new Part(e.getKey(), rem));

            // 累計已分配數量
            allocated += base;
        }

        // ========== 階段二：分配剩餘配額 ==========

        // 計算還有多少沒分配
        int remain = total - allocated;

        // 按餘數大小降序排列（餘數越大越優先）
        fracs.sort((a, b) -> Double.compare(b.frac, a.frac));

        // 將剩餘配額一個一個分給餘數最大的項目
        for (int i = 0; i < remain && i < fracs.size(); i++) {
            String k = fracs.get(i).key;  // 取得項目名稱
            result.put(k, result.get(k) + 1);  // 該項目 +1
        }

        return result;
    }

    /**
     * 建立分頁參數
     * @param size 頁面大小（取幾筆資料）
     * @return Pageable 物件（第 0 頁，size 筆資料）
     */
    private Pageable page(int size) {
        // 確保 size 至少為 1（避免無效查詢）
        return PageRequest.of(0, Math.max(1, size));
    }

    /**
     * 根據 ID 去重（保留第一次出現的單字）
     * @param list 可能包含重複單字的列表
     * @return 去重後的列表
     */
    private List<Word> deduplicateById(List<Word> list) {
        Set<Long> seen = new HashSet<>();  // 記錄已看過的 ID
        List<Word> out = new ArrayList<>(list.size());  // 結果列表

        for (Word w : list) {
            // 跳過 null 或沒有 ID 的單字
            if (w == null || w.getId() == null) continue;

            // 如果這個 ID 第一次出現（add 回傳 true）
            if (seen.add(w.getId())) {
                out.add(w);  // 加入結果列表
            }
            // 如果 ID 已存在，seen.add() 回傳 false，不加入
        }

        return out;
    }
}