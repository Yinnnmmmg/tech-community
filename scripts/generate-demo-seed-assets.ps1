$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$workspaceRoot = Split-Path -Parent $PSScriptRoot
$baseDir = Join-Path $workspaceRoot 'tech-community-web\src\main\resources\static\covers\demo\base'
$outputDir = Join-Path $workspaceRoot 'tech-community-web\src\main\resources\static\covers\demo'
$sqlPath = Join-Path $workspaceRoot 'docs\sql\demo_users_articles_with_covers.sql'

$categories = @(
    [pscustomobject]@{ Key = 'frontend'; Name = '前端'; Sort = 10; Base = (Join-Path $baseDir 'frontend-base.png'); Accent = '#FF7A59' },
    [pscustomobject]@{ Key = 'database'; Name = '数据库'; Sort = 20; Base = (Join-Path $baseDir 'database-base.png'); Accent = '#2F80ED' },
    [pscustomobject]@{ Key = 'ai'; Name = 'AI'; Sort = 30; Base = (Join-Path $baseDir 'ai-base.png'); Accent = '#F5A623' },
    [pscustomobject]@{ Key = 'java'; Name = 'Java'; Sort = 40; Base = (Join-Path $baseDir 'java-base.png'); Accent = '#F97316' },
    [pscustomobject]@{ Key = 'tools'; Name = '工具'; Sort = 50; Base = (Join-Path $baseDir 'tools-base.png'); Accent = '#22C55E' },
    [pscustomobject]@{ Key = 'interview'; Name = '面试'; Sort = 60; Base = (Join-Path $baseDir 'interview-base.png'); Accent = '#38BDF8' }
)

$users = @(
    [pscustomobject]@{
        Key = 'seed_linxiao'
        Username = 'seed_linxiao'
        Password = 'Tech123456'
        DisplayName = '林晓'
        Position = '前端工程师'
        Company = '山海科技'
        Profile = '关注体验驱动开发、工程效率和稳定交付。'
        Persona = '实战拆解'
    },
    [pscustomobject]@{
        Key = 'seed_zhaochen'
        Username = 'seed_zhaochen'
        Password = 'Tech123456'
        DisplayName = '赵宸'
        Position = '数据工程师'
        Company = '星图数据'
        Profile = '长期处理数据链路优化、稳定性治理与自动化巡检。'
        Persona = '数据视角'
    },
    [pscustomobject]@{
        Key = 'seed_jiangmu'
        Username = 'seed_jiangmu'
        Password = 'Tech123456'
        DisplayName = '蒋沐'
        Position = 'Java / AI 工程师'
        Company = '云栈实验室'
        Profile = '专注 Java 服务架构、AI 接入与团队交付效率。'
        Persona = '架构落地'
    }
)

$articles = @(
    [pscustomobject]@{ UserKey = 'seed_linxiao'; TopicKey = 'frontend'; Title = '前端组件库拆分后，如何把样式回归风险降到最低'; Summary = '从令牌、示例和回归链路三件事入手，把组件库拆分做成可验证的工程动作。'; CreatedAt = '2026-04-18 09:20:00'; File = 'linxiao-frontend-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_linxiao'; TopicKey = 'database'; Title = '业务表越堆越多时，我如何给查询链路做一次瘦身'; Summary = '先确认热点读写，再按索引、归档和接口粒度分层收敛复杂查询。'; CreatedAt = '2026-04-18 14:10:00'; File = 'linxiao-database-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_linxiao'; TopicKey = 'ai'; Title = '把 AI 助手接进后台系统后，我最先补的不是提示词'; Summary = '真正决定效果的往往是知识边界、兜底规则和调用可观测性。'; CreatedAt = '2026-04-19 10:30:00'; File = 'linxiao-ai-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_linxiao'; TopicKey = 'java'; Title = 'Java 服务接口变多以后，我用三层校验守住稳定性'; Summary = '入参、业务约束和幂等保护一起做，接口规模上来后也更容易维护。'; CreatedAt = '2026-04-19 16:00:00'; File = 'linxiao-java-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_linxiao'; TopicKey = 'tools'; Title = '把脚本、CI 和发布面板串起来后，开发效率提升了什么'; Summary = '工程工具真正有价值的地方，在于让重复动作可复用、可追踪、可交接。'; CreatedAt = '2026-04-20 11:40:00'; File = 'linxiao-tools-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_linxiao'; TopicKey = 'interview'; Title = '社招前端转全栈时，我是怎么准备系统设计面试的'; Summary = '把过往项目抽成流量、数据、容错和协作四条主线，答题更有结构。'; CreatedAt = '2026-04-20 19:05:00'; File = 'linxiao-interview-cover.png' },

    [pscustomobject]@{ UserKey = 'seed_zhaochen'; TopicKey = 'frontend'; Title = '复杂数据看板里，前端状态管理为什么一定要先定边界'; Summary = '当页面拥有多数据源与多筛选器时，状态边界比具体框架更决定维护成本。'; CreatedAt = '2026-04-21 09:00:00'; File = 'zhaochen-frontend-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_zhaochen'; TopicKey = 'database'; Title = '慢 SQL 不是一天变快的，我会先画出读写热区'; Summary = '优化从来不是一条语句的事，而是热点识别、索引验证和链路复盘的组合。'; CreatedAt = '2026-04-21 13:35:00'; File = 'zhaochen-database-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_zhaochen'; TopicKey = 'ai'; Title = '做检索增强问答时，先把知识切片规则讲明白'; Summary = '切片粒度、标题保留和更新时间策略，直接决定召回质量与答案可信度。'; CreatedAt = '2026-04-22 10:15:00'; File = 'zhaochen-ai-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_zhaochen'; TopicKey = 'java'; Title = '高并发导入任务里，Java 批处理的三个止损点'; Summary = '限流、失败隔离和进度追踪没有做好，再快的导入程序也会拖垮系统。'; CreatedAt = '2026-04-22 15:10:00'; File = 'zhaochen-java-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_zhaochen'; TopicKey = 'tools'; Title = '我常用的数据库巡检工具链，怎样做到每天自动出报告'; Summary = '把巡检脚本、告警阈值和日报模板串起来，团队才能持续看见风险。'; CreatedAt = '2026-04-23 08:50:00'; File = 'zhaochen-tools-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_zhaochen'; TopicKey = 'interview'; Title = '数据库岗位面试里，怎么把排障经历讲得有层次'; Summary = '先讲现象，再讲定位，再讲取舍和复盘，排障故事才会有说服力。'; CreatedAt = '2026-04-23 18:20:00'; File = 'zhaochen-interview-cover.png' },

    [pscustomobject]@{ UserKey = 'seed_jiangmu'; TopicKey = 'frontend'; Title = 'AI 产品首页别急着炫技，先把首屏转化路径讲清楚'; Summary = '首页越是强调智能感，越需要用清晰的信息层级把价值点先讲透。'; CreatedAt = '2026-04-24 09:25:00'; File = 'jiangmu-frontend-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_jiangmu'; TopicKey = 'database'; Title = '向量检索和关系库并存时，数据一致性要先谈什么'; Summary = '双写不是目的，围绕更新时序、重建策略和降级路径设计才是关键。'; CreatedAt = '2026-04-24 14:45:00'; File = 'jiangmu-database-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_jiangmu'; TopicKey = 'ai'; Title = '把多轮对话做稳，我最后靠的是上下文裁剪策略'; Summary = '控制上下文长度、保留关键状态、清理噪声信息，比一味堆模型参数更有效。'; CreatedAt = '2026-04-25 10:05:00'; File = 'jiangmu-ai-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_jiangmu'; TopicKey = 'java'; Title = 'Spring Boot 项目接入 AI 能力后，我会单独隔离哪几层'; Summary = '模型接入层、知识服务层和业务编排层拆开，后续替换模型和扩容都更轻松。'; CreatedAt = '2026-04-25 16:30:00'; File = 'jiangmu-java-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_jiangmu'; TopicKey = 'tools'; Title = '把本地开发环境做成一键启动后，新同学上手快了多少'; Summary = '环境一致性不是锦上添花，而是减少沟通成本和错误定位时间的基础设施。'; CreatedAt = '2026-04-26 11:00:00'; File = 'jiangmu-tools-cover.png' },
    [pscustomobject]@{ UserKey = 'seed_jiangmu'; TopicKey = 'interview'; Title = '准备 Java 面试八股时，怎样把答案讲得像真实项目'; Summary = '先给背景，再给方案，再讲线上取舍，知识点才能和实际经验真正连起来。'; CreatedAt = '2026-04-26 19:15:00'; File = 'jiangmu-interview-cover.png' }
)

function Get-CategoryMap {
    $map = @{}
    foreach ($category in $categories) {
        $map[$category.Key] = $category
    }
    return $map
}

function Get-UserMap {
    $map = @{}
    foreach ($user in $users) {
        $map[$user.Key] = $user
    }
    return $map
}

function Convert-HexToColor([string]$hex, [int]$alpha = 255) {
    $value = $hex.TrimStart('#')
    return [System.Drawing.Color]::FromArgb(
        $alpha,
        [Convert]::ToInt32($value.Substring(0, 2), 16),
        [Convert]::ToInt32($value.Substring(2, 2), 16),
        [Convert]::ToInt32($value.Substring(4, 2), 16)
    )
}

function Split-TextLines {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$Text,
        [System.Drawing.Font]$Font,
        [int]$MaxWidth
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $current = ''
    foreach ($char in $Text.ToCharArray()) {
        $candidate = $current + $char
        $size = $Graphics.MeasureString($candidate, $Font)
        if ($size.Width -le $MaxWidth -or [string]::IsNullOrEmpty($current)) {
            $current = $candidate
            continue
        }
        $lines.Add($current)
        $current = [string]$char
    }

    if (-not [string]::IsNullOrWhiteSpace($current)) {
        $lines.Add($current)
    }
    return ,$lines
}

function Write-CoverImage {
    param(
        [string]$BaseImagePath,
        [string]$OutputPath,
        [string]$Title,
        [string]$CategoryName,
        [string]$AuthorName,
        [string]$Company,
        [string]$AccentHex
    )

    $canvasWidth = 1600
    $canvasHeight = 900
    $leftPadding = 104
    $topPadding = 86

    $bitmap = New-Object System.Drawing.Bitmap($canvasWidth, $canvasHeight)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    $source = [System.Drawing.Image]::FromFile($BaseImagePath)
    try {
        $scale = [Math]::Max($canvasWidth / $source.Width, $canvasHeight / $source.Height)
        $drawWidth = [int][Math]::Ceiling($source.Width * $scale)
        $drawHeight = [int][Math]::Ceiling($source.Height * $scale)
        $drawX = [int](($canvasWidth - $drawWidth) / 2)
        $drawY = [int](($canvasHeight - $drawHeight) / 2)
        $graphics.DrawImage($source, $drawX, $drawY, $drawWidth, $drawHeight)
    } finally {
        $source.Dispose()
    }

    $overlayBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Rectangle(0, 0, [int]($canvasWidth * 0.78), $canvasHeight)),
        [System.Drawing.Color]::FromArgb(210, 10, 17, 32),
        [System.Drawing.Color]::FromArgb(18, 10, 17, 32),
        [System.Drawing.Drawing2D.LinearGradientMode]::Horizontal
    )
    $graphics.FillRectangle($overlayBrush, 0, 0, [int]($canvasWidth * 0.88), $canvasHeight)
    $overlayBrush.Dispose()

    $bottomBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Rectangle(0, [int]($canvasHeight * 0.6), $canvasWidth, [int]($canvasHeight * 0.4))),
        [System.Drawing.Color]::FromArgb(0, 10, 17, 32),
        [System.Drawing.Color]::FromArgb(160, 10, 17, 32),
        [System.Drawing.Drawing2D.LinearGradientMode]::Vertical
    )
    $graphics.FillRectangle($bottomBrush, 0, [int]($canvasHeight * 0.55), $canvasWidth, [int]($canvasHeight * 0.45))
    $bottomBrush.Dispose()

    $chipFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 19, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $titleFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 46, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $metaFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 22, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $smallFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 18, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)

    $accent = Convert-HexToColor $AccentHex
    $chipTextSize = $graphics.MeasureString($CategoryName, $chipFont)
    $chipRect = New-Object System.Drawing.RectangleF($leftPadding, $topPadding, [Math]::Ceiling($chipTextSize.Width + 40), 46)
    $chipBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(235, $accent))
    $graphics.FillRectangle($chipBrush, $chipRect.X, $chipRect.Y, $chipRect.Width, $chipRect.Height)
    $graphics.DrawString($CategoryName, $chipFont, [System.Drawing.Brushes]::White, $chipRect.X + 20, $chipRect.Y + 10)
    $chipBrush.Dispose()

    $titleLines = Split-TextLines -Graphics $graphics -Text $Title -Font $titleFont -MaxWidth 780
    $titleY = 180
    foreach ($line in $titleLines) {
        $shadowBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(100, 0, 0, 0))
        $graphics.DrawString($line, $titleFont, $shadowBrush, $leftPadding + 2, $titleY + 2)
        $shadowBrush.Dispose()
        $graphics.DrawString($line, $titleFont, [System.Drawing.Brushes]::White, $leftPadding, $titleY)
        $titleY += 66
    }

    $metaBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(235, 231, 238, 247))
    $subBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(210, 212, 223, 234))
    $graphics.DrawString("$AuthorName  ·  $Company", $metaFont, $metaBrush, $leftPadding, $titleY + 22)
    $graphics.DrawString('Tech Community Demo Seed', $smallFont, $subBrush, $leftPadding, $titleY + 60)
    $metaBrush.Dispose()
    $subBrush.Dispose()

    $chipFont.Dispose()
    $titleFont.Dispose()
    $metaFont.Dispose()
    $smallFont.Dispose()
    $graphics.Dispose()
    $bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

function New-ArticleContent {
    param(
        [pscustomobject]$Article,
        [pscustomobject]$User,
        [pscustomobject]$Category
    )

    $lead = switch ($Article.TopicKey) {
        'frontend' { "我最近在做一轮界面重构，最直接的感受是：页面代码并不可怕，可怕的是没有边界的状态和样式。$($User.DisplayName) 的做法一直很简单，先让组件层级和职责稳定下来，再谈视觉一致性和复用率。" }
        'database' { "数据库问题看上去像 SQL 语句的问题，实际上更像业务结构的问题。只要热点链路、归档边界和读写节奏没有画清楚，再多技巧都只能短暂缓解。" }
        'ai' { "AI 功能要真正接进业务系统，重点从来不只是模型能力。知识边界、数据新鲜度、失败兜底和调用成本，这些环节往往决定了功能是否能长期上线。" }
        'java' { "Java 服务做久了就会发现，稳定性不是靠单点优化换来的，而是靠边界清晰、依赖明确和异常路径可预期。只要层次清楚，项目越长越不容易失控。" }
        'tools' { "工具建设最怕做成个人经验。真正能帮团队提效的工具，一定能复用、能观测、能交接，不会因为某个脚本作者离开就失效。" }
        'interview' { "准备技术面试时，我越来越不喜欢背答案。比起把概念讲完整，更重要的是把场景、限制、取舍和结果讲清楚，这样面试官才能判断你是否真的做过。" }
        default { '' }
    }

    $core = switch ($Article.TopicKey) {
        'frontend' { @(
            '先把页面里的状态分成三类：界面即时态、服务端回填态、跨模块共享态。只有分清这三类，后续选择 store、hooks 还是局部状态才不会互相打架。',
            '样式治理不要从全量替换开始，我更倾向于先提炼颜色、间距、圆角和阴影令牌，再配合 Storybook 或示例页做差异比对。',
            '任何组件拆分都要绑定可视化回归手段。即使只是截图对比，也比口头约定更能拦住线上样式回归。'
        ) }
        'database' { @(
            '我会先画出核心读写路径，确认哪些表承担展示职责、哪些表承担统计职责、哪些字段只是历史兼容产物。',
            '慢查询优化不要一上来就加索引。先确认 where 条件、排序字段和返回列，再决定是联合索引、覆盖索引还是拆查询。',
            '对于明显只做历史回放的数据，我会直接推动冷热分层，把业务接口从历史数据拖累里解放出来。'
        ) }
        'ai' { @(
            '知识切片一定要围绕问题粒度来设计。太大就难以召回关键结论，太小又会让上下文断裂，答案看起来像拼接结果。',
            '调用链路里要保留 prompt 版本、召回片段和失败原因。没有可观测性，AI 功能上线后很难稳定迭代。',
            '我会给 AI 输出留一层业务规则兜底，比如敏感字段过滤、结果置信度不足时的降级提示，以及人工入口。'
        ) }
        'java' { @(
            '接口入参层负责挡住格式错误和缺字段，业务层负责挡住规则冲突，持久化层负责挡住幂等和并发问题。',
            '高频链路尽量减少隐式依赖，尤其是缓存、MQ、外部 API 这些跨进程调用，最好都通过清晰的适配层承接。',
            '一旦异常分支超过两层，我就会把它单独提炼成流程对象或服务对象，否则主链路很快会变得难以维护。'
        ) }
        'tools' { @(
            '工具脚本的第一目标不是炫技，而是替团队省掉重复劳动。只要一个动作每周要做三次以上，就值得沉淀为工具。',
            '我会优先补日志、失败码和参数说明。没有这些信息，工具一旦出错，新同学根本无从接手。',
            '最有效的提效方式往往是串联现有系统：本地脚本、CI、部署面板、通知机器人连起来，才算真正闭环。'
        ) }
        'interview' { @(
            '面试回答最好按背景、目标、方案、取舍、结果五段来讲。这样即使问题很散，也能快速拉回到你熟悉的项目场景里。',
            '如果题目偏系统设计，我会先说流量规模、核心数据和一致性要求，再讲缓存、异步和降级，避免一上来就堆名词。',
            '准备八股时我会把每个知识点挂到真实项目事件上。知识点一旦和故障、优化或重构经历绑定，回答就会自然很多。'
        ) }
        default { @() }
    }

    $closing = switch ($User.Persona) {
        '实战拆解' { '我更在意这套方法能不能被团队复用。只要能让协作成本下降、回归范围变小，它就值得继续投入。' }
        '数据视角' { '从数据链路角度看，很多技术问题都能被拆成观测、定位、验证三个阶段。过程越清晰，决策越稳。' }
        '架构落地' { '我习惯把方案做成分层和边界清晰的结构。这样后面无论扩容、替换还是交接，都不会推倒重来。' }
        default { '把复杂问题拆成可验证的小步骤，往往比一次性追求完美更可靠。' }
    }

    $builder = New-Object System.Text.StringBuilder
    [void]$builder.AppendLine("# $($Article.Title)")
    [void]$builder.AppendLine()
    [void]$builder.AppendLine($lead)
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('## 我会怎么拆')
    [void]$builder.AppendLine()
    foreach ($item in $core) {
        [void]$builder.AppendLine("- $item")
    }
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('## 落地建议')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine("对于 $($Category.Name) 相关的需求，我通常会先做一版最小可交付方案，再用日志、截图、压测结果或人工回访验证效果。这样既能让团队快速看到收益，也不会在第一轮投入里背上太大的维护包袱。")
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('## 结语')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine($closing)
    return $builder.ToString().Trim()
}

function Escape-SqlText([string]$Text) {
    if ($null -eq $Text) {
        return 'NULL'
    }

    $escaped = $Text.Replace('\', '\\').Replace("'", "''").Replace("`r", '')
    return "'$escaped'"
}

$categoryMap = Get-CategoryMap
$userMap = Get-UserMap

foreach ($category in $categories) {
    if (-not (Test-Path -LiteralPath $category.Base)) {
        throw "Base cover image not found: $($category.Base)"
    }
}

foreach ($article in $articles) {
    $user = $userMap[$article.UserKey]
    $category = $categoryMap[$article.TopicKey]
    $outputPath = Join-Path $outputDir $article.File
    Write-CoverImage `
        -BaseImagePath $category.Base `
        -OutputPath $outputPath `
        -Title $article.Title `
        -CategoryName $category.Name `
        -AuthorName $user.DisplayName `
        -Company $user.Company `
        -AccentHex $category.Accent
}

$quotedUsernames = ($users | ForEach-Object { "'" + $_.Username.Replace("'", "''") + "'" }) -join ', '
$sql = New-Object System.Text.StringBuilder
[void]$sql.AppendLine('-- Auto-generated by scripts/generate-demo-seed-assets.ps1')
[void]$sql.AppendLine('SET NAMES utf8mb4;')
[void]$sql.AppendLine('START TRANSACTION;')
[void]$sql.AppendLine()
[void]$sql.AppendLine(('DELETE ad FROM article_detail ad JOIN article a ON a.id = ad.article_id JOIN `user` u ON u.id = a.user_id WHERE u.username IN ({0});' -f $quotedUsernames))
[void]$sql.AppendLine(('DELETE uf FROM user_foot uf LEFT JOIN article a ON a.id = uf.document_id AND uf.document_type = 1 LEFT JOIN `user` u1 ON u1.id = uf.user_id LEFT JOIN `user` u2 ON u2.id = uf.document_user_id LEFT JOIN `user` u3 ON u3.id = a.user_id WHERE u1.username IN ({0}) OR u2.username IN ({0}) OR u3.username IN ({0});' -f $quotedUsernames))
[void]$sql.AppendLine(('DELETE ur FROM user_relation ur LEFT JOIN `user` u1 ON u1.id = ur.user_id LEFT JOIN `user` u2 ON u2.id = ur.follow_user_id WHERE u1.username IN ({0}) OR u2.username IN ({0});' -f $quotedUsernames))
[void]$sql.AppendLine(('DELETE a FROM article a JOIN `user` u ON u.id = a.user_id WHERE u.username IN ({0});' -f $quotedUsernames))
[void]$sql.AppendLine(('DELETE ui FROM user_info ui JOIN `user` u ON u.id = ui.user_id WHERE u.username IN ({0});' -f $quotedUsernames))
[void]$sql.AppendLine(('DELETE FROM `user` WHERE username IN ({0});' -f $quotedUsernames))
[void]$sql.AppendLine()
[void]$sql.AppendLine('INSERT INTO article_category (`name`, `sort`, `status`, `deleted`) VALUES')
for ($i = 0; $i -lt $categories.Count; $i++) {
    $category = $categories[$i]
    $suffix = if ($i -lt $categories.Count - 1) { ',' } else { '' }
    [void]$sql.AppendLine(("  ({0}, {1}, 1, 0){2}" -f (Escape-SqlText $category.Name), $category.Sort, $suffix))
}
[void]$sql.AppendLine('ON DUPLICATE KEY UPDATE `sort` = VALUES(`sort`), `status` = VALUES(`status`), `deleted` = VALUES(`deleted`);')
[void]$sql.AppendLine()

foreach ($category in $categories) {
[void]$sql.AppendLine(('SELECT id INTO @cat_{0} FROM article_category WHERE name = {1} LIMIT 1;' -f $category.Key, (Escape-SqlText $category.Name)))
}
[void]$sql.AppendLine()

foreach ($user in $users) {
    [void]$sql.AppendLine(('INSERT INTO `user` (third_account_id, username, password, login_type, user_role, deleted, create_time, update_time) VALUES ('''', {0}, MD5({1}), 0, 0, 0, NOW(), NOW());' -f (Escape-SqlText $user.Username), (Escape-SqlText $user.Password)))
    [void]$sql.AppendLine(('SET @u_{0} = LAST_INSERT_ID();' -f $user.Key))
    [void]$sql.AppendLine(("INSERT INTO user_info (user_id, username, photo, position, company, profile, extend, ip, deleted, create_time, update_time) VALUES (@u_{0}, {1}, '', {2}, {3}, {4}, '', JSON_OBJECT('seed', true), 0, NOW(), NOW());" -f $user.Key, (Escape-SqlText $user.DisplayName), (Escape-SqlText $user.Position), (Escape-SqlText $user.Company), (Escape-SqlText $user.Profile)))
    [void]$sql.AppendLine()
}

for ($i = 0; $i -lt $articles.Count; $i++) {
    $article = $articles[$i]
    $user = $userMap[$article.UserKey]
    $category = $categoryMap[$article.TopicKey]
    $content = New-ArticleContent -Article $article -User $user -Category $category
    $coverUrl = "/covers/demo/$($article.File)"
    $likeCount = 12 + ($i * 3)
    $commentCount = 2 + ($i % 5)
    $collectionCount = 4 + ($i % 4)
    $viewCount = 130 + ($i * 17)

    [void]$sql.AppendLine(("INSERT INTO article (user_id, article_type, title, short_title, picture, summary, category_id, source, source_url, official_stat, topping_stat, cream_stat, status, view_count, like_count, comment_count, collection_count, deleted, create_time, update_time) VALUES (@u_{0}, 1, {1}, {2}, {3}, {4}, @cat_{5}, 2, '', 0, 0, 0, 1, {6}, {7}, {8}, {9}, 0, {10}, {10});" -f $article.UserKey, (Escape-SqlText $article.Title), (Escape-SqlText $article.Title), (Escape-SqlText $coverUrl), (Escape-SqlText $article.Summary), $article.TopicKey, $viewCount, $likeCount, $commentCount, $collectionCount, (Escape-SqlText $article.CreatedAt)))
    [void]$sql.AppendLine(('SET @article_id = LAST_INSERT_ID();'))
    [void]$sql.AppendLine(("INSERT INTO article_detail (article_id, version, content, deleted, create_time, update_time) VALUES (@article_id, 1, {0}, 0, {1}, {1});" -f (Escape-SqlText $content), (Escape-SqlText $article.CreatedAt)))
    [void]$sql.AppendLine()
}

[void]$sql.AppendLine('COMMIT;')

Set-Content -Path $sqlPath -Value $sql.ToString() -Encoding utf8
Write-Output "Generated covers in: $outputDir"
Write-Output "Generated SQL file: $sqlPath"
